# CLAUDE.md — BLPS ITMO Lab3

Файл-контекст для Claude Code. Содержит всё, что нужно знать о проекте перед тем как писать код, делать ревью или планировать архитектуру. Источники: `docs/*`, `.claude/context/*`. При расхождении CLAUDE.md и кода — **код source of truth**, CLAUDE.md обновляется.

---

## ⚠️ ТЕКУЩЕЕ СОСТОЯНИЕ: переход на новую архитектуру

Проект находится в transition-период:

- **`docs/*`** — описывает **целевую (target) 3-сервисную архитектуру**: `edge-service` + `claim-service` + `penalty-service`. Это design spec, по которому ведётся рефакторинг.
- **`services/*` (текущий код)** — всё ещё **legacy 8-сервисная реализация**: `api-gateway`, `auth-service`, `claim-service`, `assessment-service`, `penalty-service`, `storage-service`, `notification-service`, `audit-service`. Это то что реально запускается сейчас.
- **`CLAUDE.md` (этот файл)** — описывает **legacy** state, чтобы агенты работали с реальным кодом до окончания миграции.

**Правила работы во время transition:**

1. При **новых задачах** (рефакторинг, новые фичи) — следовать `docs/architecture.md` и Migration plan в нём (Section 10). Цель — двигать систему к 3 сервисам.
2. При **bug fixes / точечных правках** в существующем коде — работать с текущей 8-сервисной структурой (раздел 4 ниже), потому что это реальное состояние кода.
3. Не создавать новые сервисы — только сливать существующие согласно target-плану.
4. Когда миграция завершится — этот CLAUDE.md будет переписан под 3-сервисную модель, баннер уйдёт.

Migration roadmap: [docs/architecture.md § 12 + § 10 миграция](docs/architecture.md).

---

## 1. Что это за проект (TL;DR)

**BLPS ITMO Lab3** — event-driven микросервисная система на Spring Boot 3.2 / Java 17, демонстрирующая **System Design для распределённых транзакций**: декомпозиция монолита по bounded contexts, отказ от глобального XA/2PC, choreography saga, Transactional Outbox, Inbox/idempotency, eventual consistency, manual recovery.

Бизнес-домен: обработка **заявок на штраф** (claim) между арендодателем (LANDLORD), арендатором (TENANT) и администратором (ADMIN).

### Главный архитектурный тезис

> Глобальной XA-транзакции между сервисами **нет и быть не должно**. Координация межсервисного процесса строится через **Saga + Outbox + Kafka + Idempotency**.

Старая JTA/Narayana-логика на двух Postgres из предыдущих лаб была **полностью удалена** при переходе на микросервисную архитектуру lab3. Она упоминается только как академический контраст для объяснения, почему XA не масштабируется между сервисами.

---

## 2. Технологический стек

- **Java 17**, **Spring Boot 3.2.2**, **Gradle multi-module** (Kotlin DSL)
- **PostgreSQL** — database per service (7 БД)
- **Kafka + ZooKeeper** — event backbone
- **gRPC + Protobuf** — синхронные внутренние вызовы
- **HTTP / REST + JWT** — только на edge через `api-gateway`
- **MinIO** — object storage для attachments (вне XA, через saga)
- **Spring Data JPA / Hibernate**, **Spring Kafka**, **Spring Security**, **Spring Cloud Gateway**
- **`@EnableScheduling`** для outbox relay и worker'ов

---

## 3. Структура репозитория

```
.
├── lib/
│   ├── platform-core/     # Event envelope, Outbox/Inbox, OutboxRelay, ProcessedMessageService, ApiExceptionHandler
│   └── grpc-contracts/    # protobuf + generated gRPC stubs
├── services/
│   ├── api-gateway/       # HTTP edge → gRPC → backends (port 8080)
│   ├── auth-service/      # users, roles, deactivation (8082 HTTP / 19082 gRPC)
│   ├── claim-service/     # claim lifecycle, state machine (8081 / 19081)
│   ├── assessment-service/# async worker, оценка (8083, без gRPC server)
│   ├── penalty-service/   # async worker, применение штрафа (8084 / 19084)
│   ├── storage-service/   # attachment saga init→confirm→bind (8087 / 19087)
│   ├── notification-service/ # read-side projection (8085)
│   └── audit-service/     # event trail (8086 / 19086)
├── sql/                   # init_*/drop_* DDL для каждой service-БД
├── docs/                  # архитектура, API, события, бизнес-процесс — ПЕРВОИСТОЧНИК
├── .claude/
│   ├── commands/          # /architector, /developer, /tester, /code-reviewer
│   └── context/           # lab3-plan.md, distributed-transactions-reference.md, code-map.md, playbooks.md
├── rest-client/scenarios/ # *.http demo-сценарии (30-34)
├── docker-compose.yml     # zookeeper, kafka, kafka-ui, minio, 7× postgres
├── build.gradle.kts       # Spring Boot 3.2.2, Java 17 toolchain
└── settings.gradle.kts    # multi-module setup
```

Ключевые `docs/` файлы (читай при сомнениях):

- `docs/architecture.md` — модули, bounded contexts, communication, scaling
- `docs/business-process.md` — claim lifecycle, statuses, transitions, actors
- `docs/distributed-transactions.md` — Outbox / Inbox / Saga flows
- `docs/event-catalog.md` — все события, payloads, topics, producer/consumer matrix
- `docs/api-reference.md` — HTTP / gRPC endpoints
- `docs/data-model.md` — таблицы по сервисам, инварианты
- `docs/deployment-and-operations.md` — порты, env vars, запуск
- `docs/lab3-runbook.md` — demo flows

---

## 4. Bounded Contexts и матрица ответственности

| Сервис | Владеет данными | Pub в Kafka | Sub из Kafka | Внешний доступ |
|---|---|---|---|---|
| `api-gateway` | — | — | — | HTTP edge |
| `auth-service` | `users` | `USER_DEACTIVATED` | `PENALTY_APPLIED` | через gateway / gRPC |
| `claim-service` | `claims`, `claim_timeline`, attachment refs | `CLAIM_CREATED`, `ADDITIONAL_INFO_PROVIDED`, `TENANT_RESPONSE_*`, `CLAIM_CLOSED_NO_PENALTY`, `PENALTY_APPLICATION_REQUESTED`, `ATTACHMENT_BINDING_REQUESTED` | `ASSESSMENT_*`, `PENALTY_APPLIED/FAILED`, `USER_DEACTIVATED`, `ATTACHMENT_BOUND/FAILED` | через gateway / gRPC |
| `assessment-service` | `assessment_jobs` | `ASSESSMENT_COMPLETED/FAILED` | `CLAIM_CREATED`, `ADDITIONAL_INFO_PROVIDED` | — (async worker) |
| `penalty-service` | `penalty_operations` | `PENALTY_APPLIED/FAILED` | `PENALTY_APPLICATION_REQUESTED` | через gateway / gRPC |
| `storage-service` | `attachments` | `ATTACHMENT_*` | `ATTACHMENT_BINDING_REQUESTED` | через gateway / gRPC |
| `notification-service` | `notification_log` | — | все доменные | — |
| `audit-service` | `audit_records` | — | все доменные | через gateway / gRPC |

**Жёсткие инварианты:**

- **Database per service.** Cross-DB joins ЗАПРЕЩЕНЫ. Foreign keys между схемами разных сервисов ЗАПРЕЩЕНЫ.
- `claim-service` хранит `landlord_id`, `tenant_id` как обычный `Long`, **не как ORM relation** в `auth-service`.
- Межсервисная связность — через `userId`, `claimId`, `correlationId`, `sagaId`.
- Локально внутри сервиса — strong consistency (ACID Postgres). Между сервисами — **eventual consistency**.

---

## 5. Distributed Transactions — обязательные паттерны

> Подробный академический референс: `.claude/context/distributed-transactions-reference.md`. Раздел ниже — практическое применение в этом проекте.

### 5.1. Transactional Outbox (обязателен для всех producer'ов)

**Правило:** бизнес-изменение и запись события — в **одной локальной транзакции**.

```
1. изменить business state (UPDATE/INSERT в business-таблицу)
2. INSERT в outbox_events
3. COMMIT
4. OutboxRelay (polling 1000ms) публикует в Kafka и помечает PUBLISHED
```

Таблица `outbox_events` (есть в каждой service-БД):
- `event_id`, `event_type`, `topic_name`, `event_key`
- `aggregate_type`, `aggregate_id`
- `correlation_id`, `saga_id`, `actor_id`
- `payload_json`, `status` (NEW/PUBLISHED/FAILED), `retry_count`, `error_message`
- `created_at`, `published_at`

**Запрещено:** публиковать в Kafka напрямую из бизнес-логики, минуя outbox — это dual write problem.

### 5.2. Inbox / processed_messages (обязателен для всех consumer'ов)

**Правило:** дедупликация в той же локальной транзакции, что и бизнес-эффект.

```
1. получили event
2. SELECT processed_messages WHERE event_id=? AND consumer_name=?
3. если есть → duplicate, выйти без side effect
4. если нет → выполнить локальное изменение
5. INSERT processed_messages в той же транзакции
6. COMMIT
```

Таблица `processed_messages`: `event_id`, `consumer_name`, `correlation_id`, `processed_at`.

### 5.3. Saga Choreography (без центрального orchestrator)

- Сервис публикует доменное событие → другой реагирует → коммитит локально → публикует следующее.
- Saga IDs: `claim-lifecycle-{claimId}`, `penalty-application-{claimId}`, `user-deactivation-{userId}`, `attachment-binding-{claimId}`.
- Состояние саги отражено в **бизнес-статусах** aggregate (`claims.status`, `penalty_operations.status`) и `audit_records`. Отдельной `saga_state` таблицы нет.

### 5.4. Компенсации = бизнес-действия, не rollback БД

- Penalty failure → `PENALTY_PROCESSING_FAILED` + manual retry `POST /api/penalties/operations/{id}/retry`.
- Компенсация **идемпотентна**, может ретраиться, не обязана идеально симметрично «стереть прошлое».

### 5.5. Eventual consistency — это норма, а не баг

Окна неконсистентности **проектируются явно** через промежуточные статусы:

- `ASSESSMENT_IN_PROGRESS` — claim создан, assessment ещё нет
- `PENALTY_PROCESSING` — admin принял решение, penalty ещё не применён
- `PENALTY_PROCESSING_FAILED` — penalty упал, ждёт manual recovery
- `NEED_ADDITIONAL_INFO`, `AWAITING_TENANT_RESPONSE`, `SUPPORT_REVIEW`, `PENALTY_APPLIED`, `CLOSED_NO_PENALTY`

API для long-running операций возвращает `processingStatus` + `correlationId`. Клиент опрашивает `GET /api/claims/{id}/process-status`.

### 5.6. Partition keys (порядок событий)

- Claim chain → `claimId` как event key
- User chain → `userId`
- Penalty chain → `claimId`

Локальный порядок по одному aggregate. Глобального порядка нет и не нужно.

### 5.7. Retry + DLT

- Shared Kafka error handler публикует poison messages в `<topic>.dlt`.
- Retry только для idempotent handler'ов, с backoff + jitter, лимитом попыток.

### 5.8. Timeouts + Reconciliation

- Scheduled jobs переводят зависшие операции в `*_FAILED` / `*_TIMEOUT`.
- Manual repair endpoints: `POST /api/claims/{id}/repair/reassess`, `POST /api/claims/{id}/repair/close`, `POST /api/penalties/operations/{id}/retry`.

---

## 6. Claim State Machine

Источник истины: `ClaimStatus.java`, `ClaimProcessService`, `sql/init_claim_service.sql`.

| Действие | Откуда | Куда | Кто |
|---|---|---|---|
| `POST /api/claims` | — | `ASSESSMENT_IN_PROGRESS` | LANDLORD |
| `ASSESSMENT_COMPLETED requiresAdditionalInfo=true` | `ASSESSMENT_IN_PROGRESS` | `NEED_ADDITIONAL_INFO` | assessment |
| `ASSESSMENT_COMPLETED penaltyGrounds=true` | `ASSESSMENT_IN_PROGRESS` | `AWAITING_TENANT_RESPONSE` | assessment |
| `ASSESSMENT_COMPLETED no grounds` | `ASSESSMENT_IN_PROGRESS` | `CLOSED_NO_PENALTY` | assessment |
| `POST /additional-info` | `NEED_ADDITIONAL_INFO` | `ASSESSMENT_IN_PROGRESS` | LANDLORD |
| `POST /tenant-response` | `AWAITING_TENANT_RESPONSE` | `SUPPORT_REVIEW` | TENANT |
| `POST /support-decision applyPenalty=false` | `SUPPORT_REVIEW` | `CLOSED_NO_PENALTY` | ADMIN |
| `POST /support-decision applyPenalty=true` | `SUPPORT_REVIEW` | `PENALTY_PROCESSING` | ADMIN |
| `PENALTY_APPLIED` | `PENALTY_PROCESSING` | `PENALTY_APPLIED` | penalty |
| `PENALTY_APPLICATION_FAILED` | `PENALTY_PROCESSING` | `PENALTY_PROCESSING_FAILED` | penalty |
| `USER_DEACTIVATED` | любой незакрытый | `CLOSED_NO_PENALTY` | auth |

### Эвристика assessment (демо-логика)

- Первая попытка и `claimedAmount >= 200` → `requiresAdditionalInfo=true`
- `claimedAmount >= 50` и доп. инфа не нужна → `penaltyGrounds=true`, `assessmentAmount = claimedAmount * 0.70`
- Иначе → закрытие без штрафа

---

## 7. Event Catalog (краткая выжимка)

EventEnvelope: `eventId`, `eventType`, `eventVersion=1`, `occurredAt`, `producerService`, `correlationId`, `sagaId`, `aggregateType`, `aggregateId`, `actorId`, `payload`.

| Topic | События |
|---|---|
| `claim.events` | `CLAIM_CREATED`, `ADDITIONAL_INFO_PROVIDED`, `TENANT_RESPONSE_RECEIVED`, `TENANT_RESPONSE_EXPIRED`, `CLAIM_CLOSED_NO_PENALTY`, `PENALTY_APPLICATION_REQUESTED`, `ATTACHMENT_BINDING_REQUESTED` |
| `assessment.events` | `ASSESSMENT_COMPLETED`, `ASSESSMENT_FAILED` |
| `penalty.events` | `PENALTY_APPLIED`, `PENALTY_APPLICATION_FAILED` |
| `auth.events` | `USER_DEACTIVATED` |
| `storage.events` | `ATTACHMENT_INITIALIZED`, `ATTACHMENT_CONFIRMED`, `ATTACHMENT_BOUND`, `ATTACHMENT_BINDING_FAILED` |

Полный список payloads и producer/consumer matrix — `docs/event-catalog.md`.

---

## 8. Запуск проекта

```bash
# 1. Инфраструктура
cp .env.sample .env   # при необходимости подправить порты
docker compose up -d  # zookeeper, kafka, kafka-ui, minio, 7× postgres

# Полная переинициализация схем (если volume уже был):
docker compose down -v && docker compose up -d

# 2. Сервисы (каждый в своём терминале)
./gradlew :api-gateway:bootRun
./gradlew :auth-service:bootRun
./gradlew :claim-service:bootRun
./gradlew :assessment-service:bootRun
./gradlew :penalty-service:bootRun
./gradlew :storage-service:bootRun
./gradlew :notification-service:bootRun
./gradlew :audit-service:bootRun

# 3. Сборка
./gradlew build

# 4. Тесты конкретного модуля
./gradlew :claim-service:test
```

Демо-сценарии: `rest-client/scenarios/30-34.http` через `http://localhost:8080`.

---

## 9. Правила для контекста (общие)

1. **Source of truth — код.** При расхождениях между docs и кодом — кода больше доверия. Документация догоняет код, не наоборот.
2. **Не дублируй данные между сервисами.** Нужен пользователь в `claim-service` → `AuthRpcService.GetUser` по gRPC, не локальная копия `users`.
3. **Любая новая публикация события — через Outbox.** Не `kafkaTemplate.send(...)` из бизнес-кода.
4. **Любая новая обработка события — через ProcessedMessageService.** Иначе придёт второй раз и сломает данные.
5. **Любой новый статус — это бизнес-факт.** Регистрируй в `ClaimStatus`, документируй переход в `docs/business-process.md`, пиши запись в `claim_timeline`.
6. **Новый Kafka event — это контракт.** Добавляй в `EventType`, `TopicNames`, payload class в `platform-core`, обновляй `docs/event-catalog.md`.
7. **HTTP только на edge.** Между сервисами sync — gRPC из `grpc-contracts`. Не плоди REST-вызовы сервис-сервис.
8. **Демо-сценарии в `rest-client/scenarios/*.http` — это интеграционный smoke test.** После любого изменения публичного API проверь, что соответствующий `.http` файл всё ещё работает или обнови его.
9. **JPA `ddl-auto=update` сейчас работает**, но `sql/init_*.sql` — формальная спецификация схемы. При изменениях модели обновляй и Java entity, и SQL-init.

---

## 10. Anti-patterns — никогда не делай

- ❌ Прямой `kafkaTemplate.send(...)` из бизнес-метода без записи в outbox.
- ❌ Consumer без проверки `processed_messages`.
- ❌ Кросс-сервисный JPA-join или foreign key между схемами.
- ❌ Глобальный `@Transactional` поверх gRPC/HTTP-вызова в другой сервис.
- ❌ Использование 2PC/XA для новых межсервисных операций.
- ❌ Синхронный HTTP-вызов сервис-сервис в обход gRPC контракта.
- ❌ Обещание клиенту read-your-own-writes для async flow — отдавай 202 + processingStatus.
- ❌ Компенсация, которая не идемпотентна.
- ❌ Retry без лимита / без backoff / на неидемпотентной операции.
- ❌ `catch (Exception e) { log.error(...); }` без проброса и без перевода aggregate в failure state.
- ❌ Hardcoded порты / connection strings — всё через env.

---

## 11. Правила для ролей (`/architector`, `/developer`, `/tester`, `/code-reviewer`)

Полные роли описаны в `.claude/commands/*.md`. Здесь — **проектные оверрайды**, которые применяются поверх ролевой инструкции.

### Общие для всех ролей

- Перед началом работы — прочитай `docs/architecture.md` + раздел `docs/distributed-transactions.md`, релевантный задаче.
- Любое предложение, нарушающее `Section 10. Anti-patterns`, отвергается без обсуждения.
- Все примеры и предложения — на текущем стеке (Java 17, Spring Boot 3.2.2, Kafka, gRPC, PostgreSQL). Не предлагай миграцию на другой стек без явного запроса.
- При обсуждении распределённых транзакций обязательно явно называй паттерн (Outbox / Inbox / Saga / Compensation / Idempotency) — не «как-нибудь надёжно отправить событие».

### `/architector` — оверрайды

- Декомпозиция новых фич — по существующим bounded contexts. Новый сервис создаётся только если есть **новый bounded context** с собственной БД и owning team.
- Любое предложение «сделать join между сервисами» → перепроектируй через event или gRPC.
- Async-процесс по умолчанию = **choreography saga**, без отдельного orchestrator engine (Camunda/Temporal), пока пользователь явно не попросит.
- Coordination ≠ shared state. Координация — через события + correlationId/sagaId.
- При предложении новой технологии — обоснуй, **какую конкретную проблему текущей архитектуры она решает**. Хайп ≠ обоснование.

### `/developer` — оверрайды

- **Producer-чеклист** для любого нового события:
  1. Бизнес-update и `outbox.append(...)` — в одной `@Transactional`.
  2. Использовать `OutboxService` / `OutboxRelay` из `platform-core`, не писать свой.
  3. `eventKey = aggregateId`, `correlationId` / `sagaId` проброшены.
  4. Добавить `EventType`, payload class, обновить producer/consumer matrix в `docs/event-catalog.md`.
- **Consumer-чеклист**:
  1. `@KafkaListener` обёрнут `ProcessedMessageService.handleIfNew(...)` (или эквивалент из `platform-core`).
  2. Бизнес-изменение и запись `processed_messages` — в одной транзакции.
  3. Идемпотентность — даже если транспорт обещает at-least-once.
  4. Падение handler'а → DLT (через shared error handler), без бесконечного retry.
- **Транзакции:**
  - Никаких удалённых вызовов (gRPC / HTTP / Kafka send) **внутри** `@Transactional` — есть риск зависшей БД-транзакции.
  - `@Transactional` не работает на `private` методах и при self-invocation — знай это.
  - Для конкурентных update'ов aggregate — `@Version` (optimistic) по умолчанию.
- **Статусы:** новый статус → добавь в `ClaimStatus`, валидируй переход в `ClaimProcessService`, пиши `claim_timeline`.
- **gRPC:** контракты строго в `lib/grpc-contracts` (proto). Не плоди REST-вызовы между сервисами.
- **Без feature flags / без backward-compat shim'ов**, пока пользователь не попросит. Это учебный проект.

### `/tester` — оверрайды

- Integration-тесты только на **Testcontainers с реальным PostgreSQL / Kafka / MinIO**. H2 / embedded Kafka запрещены — поведение SQL и broker'а отличается, словишь false positive.
- Обязательные сценарии для distributed flows (любой PR, трогающий saga / outbox / consumer):
  - **Outbox happy path** — бизнес-update + outbox row в одной транзакции; relay публикует.
  - **Duplicate delivery** — то же событие приходит дважды, side effect однократный, `processed_messages` содержит ровно одну запись.
  - **Consumer rollback** — handler бросает, processed_messages **не** записан, бизнес-изменение откатано.
  - **Saga failure path** — для penalty: failure → `PENALTY_PROCESSING_FAILED` → manual retry → `PENALTY_APPLIED`.
- Async проверки — `Awaitility`, **никаких `Thread.sleep`**.
- Smoke-проверка `.http` сценариев (`rest-client/scenarios/30-34.http`) после изменений публичного API — обязательна.
- Имена тестов: `should_<expected>_when_<condition>`. AAA / Given-When-Then.

### `/code-reviewer` — оверрайды

В дополнение к базовому ревью обязательно проверь:

1. **Outbox**: каждый новый publish идёт через outbox, нет прямого `kafkaTemplate.send` в бизнес-коде.
2. **Inbox**: каждый новый consumer идёт через `processed_messages`, дедупликация атомарна с бизнес-update.
3. **Transactional boundary**: нет gRPC / HTTP / Kafka send внутри `@Transactional`. Нет `@Transactional` на private методах. Нет self-invocation.
4. **Cross-context isolation**: нет cross-DB join'ов, нет ORM relation в чужой сервис, нет дублирования owned data.
5. **State machine**: новые переходы валидируются явно, claim_timeline пишется, новые статусы документированы.
6. **Event contract**: новые события зарегистрированы в `EventType` / `TopicNames`, payload иммутабельный, `eventKey = aggregateId`.
7. **Идемпотентность**: handler можно вызвать дважды без побочного эффекта.
8. **Failure handling**: retry policy, DLT, timeout, repair endpoint — присутствуют для long-running шагов.
9. **API contract**: long-running операция возвращает 202 + processingStatus + correlationId, не врёт про синхронность.
10. **Gost-mode / humanize**: убрать AI-признаки (см. `.claude/commands/code-reviewer.md` раздел gost-mode) — пустые javadoc, избыточные null-checks, чрезмерные имена, ненужные абстракции.

---

## 12. Демо-сценарии для защиты lab3

Должны работать always. Если PR ломает любой из этих — это **BLOCKER**.

1. **Outbox dual-write resilience** — claim создан в БД, Kafka была недоступна, relay допубликовал после восстановления.
2. **Eventual consistency** — `GET /claims/{id}` сначала `ASSESSMENT_IN_PROGRESS`, потом догоняется до финального статуса.
3. **Duplicate event handling** — одно `PENALTY_APPLIED` доставлено дважды, claim перешёл ровно один раз, `processed_messages` дедуплицировал.
4. **Penalty failure + manual recovery** — admin со `simulateFailure=true` → `PENALTY_PROCESSING_FAILED` → `POST /penalties/operations/{id}/retry` → `PENALTY_APPLIED`.
5. **User deactivation distributed reaction** — `POST /auth/users/{id}/deactivate` → `claim-service` асинхронно закрывает open claims.
6. **MinIO outside XA** — attachment `init → confirm → bind` через saga, без XA с MinIO.

Полный runbook: `docs/lab3-runbook.md`.

---

## 13. Что НЕ реализовано (честность)

Чтобы предложения архитектора и developer'а учитывали реальный scope, а не идеальный мир:

- Нет schema registry (Avro/Protobuf) для Kafka.
- Нет distributed tracing stack (OpenTelemetry / Jaeger).
- Нет metrics stack (Prometheus / Grafana).
- Нет централизованных логов.
- Нет DLT monitoring UI / alerting.
- Нет production-grade OAuth2/service-to-service auth — demo JWT.
- Сервисы запускаются вне контейнеров (`./gradlew :…:bootRun`).
- Один Kafka broker, один ZooKeeper, одна БД на сервис без HA — это **demo**, не prod cluster.

При предложении этих штук — обозначь как future work и обоснуй необходимость для текущей цели lab3.

---

## 14. Полезные ссылки внутри репо

- `.claude/context/lab3-plan.md` — подробный план lab3, demo-сценарии, обоснования решений
- `.claude/context/distributed-transactions-reference.md` — академический референс по distributed transactions (XA, Saga, Outbox, Inbox, TCC, eventual consistency, idempotency, retry, timeout, reconciliation, partition ordering)
- `.claude/context/code-map.md` — карта ключевых файлов по сервисам и матрица «что обновить вместе» при типичных изменениях
- `.claude/context/playbooks.md` — task-specific playbook'и (lifecycle change, event contract change, gateway change, attachment saga, penalty flow, и т.д.)
- `.claude/commands/*.md` — описание ролей `/architector`, `/developer`, `/tester`, `/code-reviewer`
- `docs/uml/`, `docs/bpmn/` — диаграммы (BPMN — **reference model**, не runtime)
