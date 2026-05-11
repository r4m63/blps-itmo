# Distributed Transactions Design

## 1. Проблема, которую решает архитектура

Система обрабатывает межсервисный бизнес-процесс **без глобального transaction manager**. Основные риски, которые нужно митигировать:

1. **Dual write problem.** Бизнес-данные записались в БД, но событие в Kafka не ушло (или наоборот) — данные и события расходятся.
2. **Duplicate delivery.** Сообщение пришло consumer'у несколько раз — нельзя дважды списать penalty, увеличить penalty_count, и т.д.
3. **Partial failure в середине саги.** Один сервис уже закоммитил локально, а другой не смог продолжить.
4. **Невозможность XA over external systems.** MinIO, внешний penalty processor, Kafka — нельзя включить в один глобальный commit.
5. **Stale reads.** Клиент только что создал claim, но `GET` ещё не показывает финальный статус.
6. **Зависшие процессы.** Worker не дотянул шаг до конца, claim застрял в intermediate state.

## 2. Почему XA / 2PC не используется

Решение **отказаться** от глобального XA взято осознанно:

- **Микросервисы владеют разными БД.** Глобальный XA-coordinator нужен только если все ресурсы XA-aware и сидят в одной координационной зоне. Это противоречит database-per-service.
- **Kafka не должна быть XA-участником вместе с бизнес-БД.** Это резко ухудшает throughput и доступность.
- **Coordinator становится SPOF.** При его падении все участники зависают с открытыми prepared транзакциями.
- **Внешние side-effects вне XA.** Применение штрафа, отправка в MinIO, отправка email — это **необратимые** действия. Их нельзя откатить generic rollback'ом.
- **2PC блокирующий протокол.** Latency повышается, availability падает.

В коде остаётся **исторический артефакт** Narayana/JTA из старой архитектуры lab1/lab2 (два datasource в одном Spring Boot процессе). Этот код **не используется** в lab3 и оставлен только как контраст для объяснения, почему XA не масштабируется.

## 3. Применяемые паттерны (сводка)

| Паттерн | Где применяется | Что решает |
|---|---|---|
| **Local transaction** | каждый write use case в каждом сервисе | базовая ACID на одну БД |
| **Transactional Outbox** | все 3 сервиса | dual write problem |
| **Inbox / processed_messages** | все consumer'ы | duplicate delivery, idempotency |
| **Saga choreography** | penalty flow, user deactivation, attachment binding | межсервисный процесс без XA |
| **Compensating actions** | penalty failure → manual retry / close | non-rollbackable side-effect |
| **Eventual consistency** | межсервисные транзакции | отказ от мгновенной глобальной согласованности |
| **Optimistic locking** (`@Version`) | claim updates | конкурентный update aggregate |
| **Idempotency keys** | retry-чувствительные операции | повторные вызовы не дублируют эффект |
| **DLT (Dead Letter Topic)** | shared Kafka error handler | poison messages |
| **Timeout handling** | tenant response (P3D), penalty processing | зависшие шаги |
| **Manual recovery** | `POST /penalties/operations/{id}/retry` + `/claims/{id}/repair/*` | failure при исчерпанных авто-ретраях |
| **Partition keys** (Kafka) | `claimId` / `userId` как event key | локальный порядок по aggregate |

## 4. Transactional Outbox

### Правило

Бизнес-изменение и запись события — **в одной локальной транзакции**:

```text
@Transactional {
  1. UPDATE/INSERT business state
  2. INSERT outbox_events (event_type, payload_json, ...)
  COMMIT
}
3. OutboxRelay (отдельный @Scheduled поток, 1000ms poll) → Kafka
4. UPDATE outbox_events SET status='PUBLISHED', published_at=now() WHERE id=?
```

Если Kafka недоступна, бизнес-данные **уже** записаны. Событие лежит в outbox со status=`NEW`, relay попробует снова. Это решает **dual write problem**.

### Таблица `outbox_events`

| Поле | Тип | Назначение |
|---|---|---|
| `id` | BIGSERIAL | первичный ключ |
| `event_id` | UUID UNIQUE | сквозной id события (попадает в EventEnvelope) |
| `event_type` | VARCHAR | `EventType` enum |
| `topic_name` | VARCHAR | Kafka topic (`claim.events`, `penalty.events`, `identity.events`) |
| `event_key` | VARCHAR | partition key (обычно = `aggregate_id`) |
| `aggregate_type` | VARCHAR | `CLAIM` / `USER` / `PENALTY` |
| `aggregate_id` | VARCHAR | id aggregate, к которому относится событие |
| `correlation_id` | VARCHAR | сквозной id бизнес-процесса |
| `saga_id` | VARCHAR | id конкретной саги |
| `actor_id` | BIGINT | пользователь, инициировавший действие |
| `payload_json` | JSONB | сериализованный payload |
| `status` | VARCHAR | `NEW` / `PUBLISHED` / `FAILED` |
| `retry_count` | INT | количество попыток relay |
| `error_message` | TEXT | последняя ошибка relay |
| `created_at` | TIMESTAMPTZ | момент создания |
| `published_at` | TIMESTAMPTZ NULL | момент публикации |

Эта таблица **есть в каждой service-БД** (`blps_identity`, `blps_claim`, `blps_penalty`).

### Запрещено

- Прямой `kafkaTemplate.send(...)` из бизнес-кода — нарушает атомарность с БД.
- Запись в outbox из gRPC handler'а в чужой БД — пишем только в свою.

### Реализация

- `OutboxService.append(...)` (в `platform-core`) — единственный способ добавить событие в outbox.
- `OutboxRelay` (в `platform-core`) — `@Scheduled(fixedDelay=1000)` поллит NEW events, публикует в Kafka, помечает PUBLISHED. При ошибке — `retry_count++`, status=`FAILED`.
- После 5 неудач — алёрт через `/actuator/health` indicator (custom). Дальше — ручной разбор.

## 5. Inbox / processed_messages

### Правило

Дедупликация и бизнес-эффект — **в одной локальной транзакции**:

```text
@Transactional {
  1. SELECT processed_messages WHERE event_id=? AND consumer_name=?
  2. if exists → COMMIT, выйти без side-effect (duplicate)
  3. if not exists → выполнить бизнес-изменение
  4. INSERT processed_messages (event_id, consumer_name, ...)
  COMMIT
}
```

Это защищает от:

- повторной доставки Kafka (at-least-once semantics)
- повторного запуска consumer после сбоя до commit'а offset'а
- consumer rebalance'а в момент обработки

### Таблица `processed_messages`

| Поле | Тип | Назначение |
|---|---|---|
| `id` | BIGSERIAL | первичный ключ |
| `event_id` | UUID | id события (из EventEnvelope) |
| `consumer_name` | VARCHAR | имя consumer'а (например `claim-service:PenaltyAppliedHandler`) |
| `correlation_id` | VARCHAR | для трассировки |
| `processed_at` | TIMESTAMPTZ DEFAULT now() | момент обработки |

`UNIQUE (event_id, consumer_name)` — гарантирует идемпотентность даже при race condition.

### Реализация

`ProcessedMessageService.handleIfNew(eventId, consumerName, () -> businessAction)` — обёртка, которая делает SELECT, в случае duplicate возвращает молча, иначе вызывает action и пишет processed row. Всё внутри одной `@Transactional`.

## 6. Saga Choreography

### Принцип

Нет central orchestrator. Каждый сервис:

1. реагирует на доменное событие (`@KafkaListener`)
2. в локальной @Transactional выполняет:
   - dedup через processed_messages
   - бизнес-update своего aggregate
   - INSERT outbox_events со следующим событием
3. relay публикует — следующий участник реагирует

Состояние саги отражено в **бизнес-статусах** aggregate (например, `claims.status`, `penalty_operations.status`). Отдельной таблицы `saga_state` нет.

### Активные саги

#### Saga 1: `claim-lifecycle-{claimId}`

В пределах одного claim-service (self-loop через Kafka):

```
CLAIM_CREATED → assessment job → ASSESSMENT_COMPLETED (locally) → claim.status update
```

Технически self-saga, но через Kafka, чтобы продемонстрировать outbox/inbox даже в локальном async flow.

#### Saga 2: `penalty-application-{claimId}`

Межсервисная:

```
[claim-service]   support-decision → PENALTY_APPLICATION_REQUESTED   (claim status: PENALTY_PROCESSING)
                                          ↓ Kafka
[penalty-service] penalty_operation PENDING → PROCESSING → APPLIED   PENALTY_APPLIED
                                          ↓ Kafka
[claim-service]   claim.status = PENALTY_APPLIED                     (final)
[edge-service]    users.penalty_count++                              (parallel reaction)
```

#### Saga 3: `user-deactivation-{userId}`

```
[edge-service]  users.enabled=false → USER_DEACTIVATED
                                          ↓ Kafka
[claim-service] find open claims for user → close each as CLOSED_NO_PENALTY
```

#### Saga 4: `attachment-binding-{claimId}`

Локальная (в claim-service) с **внешним** non-XA участником MinIO:

```
[claim-service] init → metadata row INITIALIZED  +  presigned PUT URL
[client]        upload directly to MinIO         ←  out of band
[claim-service] confirm → HEAD-check MinIO       →  metadata CONFIRMED
[claim-service] createClaim with attachment refs →  metadata BOUND, claim.attachments populated
```

MinIO **не участвует в транзакции БД**. Если client загрузил в MinIO но не вызвал confirm — reconciliation job помечает attachment как ORPHANED через 24h.

## 7. Compensating Actions

Компенсация — это **бизнес-действие**, не rollback БД.

### Penalty failure

| Шаг исходный | Компенсирующее действие |
|---|---|
| `PENALTY_APPLICATION_REQUESTED` → `PENALTY_PROCESSING` | claim переводится в `PENALTY_PROCESSING_FAILED`, ждёт ADMIN'а |
| `penalty_operation.status = FAILED` | `POST /penalties/operations/{id}/retry` возвращает в `PENDING`, worker обрабатывает заново |

Компенсация **идемпотентна**: retry можно вызывать несколько раз, эффект тот же.

### Claim manual repair

| Endpoint | Действие |
|---|---|
| `POST /api/claims/{id}/repair/reassess` | сбросить status в `ASSESSMENT_IN_PROGRESS`, создать новый assessment_job |
| `POST /api/claims/{id}/repair/close` | принудительно закрыть как `CLOSED_NO_PENALTY` с note |

Используется когда автоматические механизмы (retry, timeout) исчерпаны или дают непредсказуемое поведение.

## 8. Eventual Consistency

### Контракт с клиентом API

Long-running операции возвращают:

- **HTTP 202 Accepted**
- `claimId`, `correlationId`, `processingStatus` (например `ASSESSMENT_IN_PROGRESS`)
- НЕ обещают финальный статус сразу

Клиент опрашивает:

- `GET /api/claims/{id}/process-status` — текущий бизнес-статус + флаг `terminal: true/false`
- `GET /api/claims/{id}/timeline` — история переходов с timestamp'ами

### Intermediate-статусы как часть архитектуры

| Статус | Назначение |
|---|---|
| `ASSESSMENT_IN_PROGRESS` | claim создан, assessment ещё работает |
| `PENALTY_PROCESSING` | support решил применить штраф, penalty-service применяет |
| `PENALTY_PROCESSING_FAILED` | penalty упал, ждёт ADMIN |
| `NEED_ADDITIONAL_INFO` | landlord должен дослать материалы |
| `AWAITING_TENANT_RESPONSE` | ждём ответ tenant (timeout P3D) |
| `SUPPORT_REVIEW` | ждём решения support |

**Нельзя обещать клиенту read-your-own-writes для async flow.** Это намеренный выбор, не баг.

## 9. Idempotency

### Где обязательна

- **Все Kafka consumers** — через `processed_messages`
- **`POST /penalties/operations/{id}/retry`** — повторный retry FAILED операции не создаёт дубликат
- **Outbox events** — `event_id` unique, повторная публикация не создаёт два события

### Не реализовано (явно)

`POST /api/claims` **не** имеет `Idempotency-Key` header'а. Клиент при retry может создать дубликат. Это осознанный недостаток demo-уровня — для production нужно добавить middleware на edge-service, который проверяет header против таблицы `processed_requests`.

## 10. Retry policy + DLT

### Kafka consumers

В `PlatformKafkaConfig` (в `platform-core`) настроен shared error handler:

- `DefaultErrorHandler` с `ExponentialBackOff` (initial=1s, multiplier=2.0, maxInterval=30s, maxRetries=5)
- non-retryable exceptions (`IllegalArgumentException`, validation errors) — сразу в DLT без retry
- после исчерпания попыток — событие публикуется в `<topic>.dlt` с заголовками о причине

### Топики DLT

- `claim.events.dlt`
- `penalty.events.dlt`
- `identity.events.dlt`

### Что НЕ делается

- Бесконечный retry — никогда. После N попыток событие в DLT, оператор разбирается.
- Retry на неидемпотентной операции — каждое retry-чувствительное место защищено через `processed_messages`.

## 11. Timeout handling

### Tenant response (P3D)

`@Scheduled(fixedDelay=60000)` в claim-service:

```sql
SELECT id FROM claims
WHERE status = 'AWAITING_TENANT_RESPONSE'
  AND now() - updated_at > interval '3 days'
FOR UPDATE SKIP LOCKED LIMIT 50;
```

Для каждой claim: @Transactional → `SUPPORT_REVIEW` + timeline + outbox `TENANT_RESPONSE_EXPIRED`.

`FOR UPDATE SKIP LOCKED` гарантирует, что параллельные реплики claim-service не возьмут одну и ту же claim.

### Penalty processing

Если `penalty_operations.status` остался в `PROCESSING` дольше 10 минут — reconciliation job помечает как FAILED. Worker может вызвать `POST /retry`.

### Outbox publish

Если outbox event остался `NEW` дольше 5 минут — алёрт через actuator. Это сигнал, что relay сломался или Kafka недоступна.

## 12. Partition keys

Стандарт: `eventKey = aggregateId`.

| Поток | Aggregate | Key |
|---|---|---|
| claim lifecycle | claim | `claimId` |
| penalty saga | claim | `claimId` |
| user deactivation | user | `userId` |

Это даёт **локальный порядок** событий по одному aggregate. Глобального порядка нет и не требуется.

## 13. correlationId, sagaId, eventId

### Семантика

| ID | Смысл | Жизнь |
|---|---|---|
| `eventId` | unique id одного события | живёт в outbox/inbox/audit |
| `correlationId` | сквозной id одного бизнес-процесса | передаётся через HTTP header `X-Correlation-Id`, через EventEnvelope, через gRPC metadata |
| `sagaId` | id конкретной саги | формируется по шаблону, см. ниже |

### Шаблоны sagaId

- `claim-lifecycle-{claimId}` — для async-обработки одной claim
- `penalty-application-{claimId}` — для penalty-saga
- `user-deactivation-{userId}` — для каскадного закрытия claims
- `attachment-binding-{claimId}` — для MinIO saga

### MDC и logs

`correlationId` кладётся в MDC при входе в HTTP / gRPC / Kafka handler. Все structured-логи содержат это поле, что позволяет восстановить цепочку через `grep`/Loki.

## 14. Failure handling matrix

| Сценарий | Решение |
|---|---|
| Postgres commit OK, Kafka unavailable | outbox → relay retry |
| Kafka delivered twice | processed_messages dedup |
| Consumer crashes mid-handler | offset не закоммичен → перечитается → dedup |
| `@Transactional` rollback в consumer | processed_messages не записан → переобработка |
| External penalty processor down | `penalty_operation.status=FAILED` → claim в `PENALTY_PROCESSING_FAILED` → manual retry |
| Tenant не отвечает 3+ дня | scheduled timeout → `SUPPORT_REVIEW` |
| Concurrent update одного claim | optimistic lock `@Version` → второй commit получает `OptimisticLockException` → retry или fail |
| Poison message в Kafka | shared error handler → DLT после N retries |
| MinIO upload оборвался до confirm | reconciliation job помечает ORPHANED через 24h |
| Long-running saga застряла | `claim_timeline` + `audit_events` view + manual repair endpoint |

## 15. Что показывать на защите лабы

Минимальный набор demo для зачёта (см. [runbook.md](runbook.md)):

1. **Dual-write resilience.** Kafka down → claim создан, событие лежит в outbox → Kafka up → relay допубликовал.
2. **Eventual consistency.** Создал claim → `GET` показывает `ASSESSMENT_IN_PROGRESS` → через несколько секунд догоняется до финального.
3. **Duplicate event handling.** Послать `PENALTY_APPLIED` дважды (через kafka-console-producer) → claim перешёл ровно один раз, в `processed_messages` ровно одна запись.
4. **Penalty failure + manual recovery.** support-decision с `simulateFailure=true` → `PENALTY_PROCESSING_FAILED` → retry → `PENALTY_APPLIED`.
5. **User deactivation.** Деактивировать LANDLORD → его открытые claims закрылись через несколько секунд.
6. **MinIO outside XA.** init → upload → confirm → bind → orphan reconciliation на брошенном attachment.

Если хотя бы один из 6 ломается — это **BLOCKER** для PR.
