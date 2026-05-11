# Architecture

## 1. Цели

Архитектура построена под три цели:

1. Декомпозировать домен «заявка на штраф» на bounded contexts с собственными БД.
2. Отказаться от глобальных распределённых ACID-транзакций (XA/2PC).
3. Продемонстрировать end-to-end event-driven flow с saga, outbox, inbox и manual recovery.

Это **учебный System Design** для лабораторной работы ИТМО (BLPS), а не production cloud platform. Дизайн ориентирован на **демонстрацию паттернов**, а не на абсолютную HA.

## 2. Архитектурный стиль

- microservices (3 сервиса)
- database-per-service
- HTTP API edge через `edge-service`
- gRPC для внутренних sync вызовов
- event-driven integration через Kafka
- saga choreography (без центрального orchestrator engine)
- transactional outbox + inbox/processed_messages
- eventual consistency между сервисами

## 3. Модули проекта

### Бизнес-сервисы (3)

| Сервис | Bounded Context | Расположение |
|---|---|---|
| `edge-service` | Identity + API edge | `services/edge-service/` |
| `claim-service` | Claim lifecycle (включая attachments, assessment, notifications, timeline) | `services/claim-service/` |
| `penalty-service` | Penalty side-effect | `services/penalty-service/` |

### Общие библиотеки

| Модуль | Содержимое | Расположение |
|---|---|---|
| `platform-core` | `EventEnvelope`, `EventType`, `TopicNames`, payload-классы, `OutboxService`, `OutboxRelay`, `ProcessedMessageService`, общий `ApiExceptionHandler`, `DemoJwtService`, Kafka config, gRPC lifecycle | `lib/platform-core/` |
| `grpc-contracts` | protobuf-контракты и generated stubs | `lib/grpc-contracts/` |

## 4. Bounded Contexts

### `edge-service`

**Отвечает за:**

- внешний HTTP перимetr (`/api/**`), JWT-аутентификацию, propagation `X-User-Id` / `X-User-Role` / `X-Correlation-Id`
- пользователей (id, email, role: LANDLORD / TENANT / SUPPORT / ADMIN)
- penalty count пользователя
- деактивацию пользователя как бизнес-факт

**Не отвечает за:**

- claim, attachment, penalty операции (только проксирование вызовов)

**Kafka:**

- publisher: `USER_DEACTIVATED`
- consumer: `PENALTY_APPLIED` → `++penalty_count`

### `claim-service`

**Отвечает за:**

- claim aggregate и его state machine
- `claim_timeline` (история переходов и значимых событий)
- attachments: metadata + saga `INIT → CONFIRM → BIND` с MinIO как non-XA участником
- assessment: in-process async worker, эвристика оценки
- tenant response: 3-дневный timeout (BPMN P3D)
- support decision
- notifications: write-side log при transition'ах (без отдельного сервиса)

**Не отвечает за:**

- идентичность пользователей (валидация через gRPC в `edge-service`)
- применение штрафа (только публикует `PENALTY_APPLICATION_REQUESTED`)

**Kafka:**

- publisher: `CLAIM_CREATED`, `ADDITIONAL_INFO_PROVIDED`, `ASSESSMENT_COMPLETED`, `ASSESSMENT_FAILED`, `TENANT_RESPONSE_RECEIVED`, `TENANT_RESPONSE_EXPIRED`, `CLAIM_CLOSED_NO_PENALTY`, `PENALTY_APPLICATION_REQUESTED`, `ATTACHMENT_INITIALIZED`, `ATTACHMENT_CONFIRMED`, `ATTACHMENT_BOUND`
- consumer: `PENALTY_APPLIED`, `PENALTY_APPLICATION_FAILED`, `USER_DEACTIVATED`, self-consume `CLAIM_CREATED` / `ADDITIONAL_INFO_PROVIDED` (для assessment worker), `ASSESSMENT_COMPLETED` / `ASSESSMENT_FAILED` (для status transition)

### `penalty-service`

**Отвечает за:**

- async применение penalty side-effect (имитация внешнего процессора)
- `penalty_operations` lifecycle: `PENDING → PROCESSING → APPLIED|FAILED`
- manual retry FAILED операций

**Не отвечает за:**

- claim status (только сигналит событие; перевод status делает `claim-service`)
- penalty count в `users` (это делает `edge-service`)

**Kafka:**

- publisher: `PENALTY_APPLIED`, `PENALTY_APPLICATION_FAILED`
- consumer: `PENALTY_APPLICATION_REQUESTED`

## 5. Communication

### Внешние HTTP вызовы

Клиенты ходят **только** в `edge-service` на `http://localhost:8080`:

- `POST /api/auth/login` → demo JWT
- `Authorization: Bearer <token>` для всех остальных вызовов
- `edge-service` проксирует в backend-сервисы через **gRPC**

### Синхронные внутренние вызовы

gRPC-контракты из `lib/grpc-contracts/src/main/proto/`:

| Контракт | Реализуется в | Вызывается из |
|---|---|---|
| `IdentityRpcService` | `edge-service` | `claim-service` (валидация actor'а) |
| `ClaimRpcService` | `claim-service` | `edge-service` (проксирование REST) |
| `PenaltyRpcService` | `penalty-service` | `edge-service` (проксирование REST) |

REST-контроллеры в `claim-service` / `penalty-service` могут существовать **только** для прямого локального debug, но они **не контракт** межсервисной интеграции.

### Асинхронные вызовы

Все межсервисные бизнес-события — через Kafka:

| Topic | Описание |
|---|---|
| `claim.events` | События lifecycle заявки (включая attachment-стадии) |
| `penalty.events` | События применения штрафа |
| `identity.events` | События идентичности (деактивация) |
| `<topic>.dlt` | Dead Letter Topic для poison messages |

## 6. Матрица ответственности

| Сервис | Владеет данными | Pub в Kafka | Sub из Kafka | Внешний доступ |
|---|---|---|---|---|
| `edge-service` | `users` | `USER_DEACTIVATED` | `PENALTY_APPLIED` | HTTP edge + gRPC `IdentityRpcService` |
| `claim-service` | `claims`, `claim_timeline`, `attachments`, `assessment_jobs`, `notifications` | `CLAIM_CREATED`, `ADDITIONAL_INFO_PROVIDED`, `ASSESSMENT_*`, `TENANT_RESPONSE_*`, `CLAIM_CLOSED_NO_PENALTY`, `PENALTY_APPLICATION_REQUESTED`, `ATTACHMENT_*` | `PENALTY_APPLIED/FAILED`, `USER_DEACTIVATED`, self-loop `CLAIM_CREATED`/`ADDITIONAL_INFO_PROVIDED`/`ASSESSMENT_*` | gRPC `ClaimRpcService` |
| `penalty-service` | `penalty_operations` | `PENALTY_APPLIED`, `PENALTY_APPLICATION_FAILED` | `PENALTY_APPLICATION_REQUESTED` | gRPC `PenaltyRpcService` |

## 7. Топология данных

Три физических Postgres базы:

- `blps_identity` (edge-service)
- `blps_claim` (claim-service)
- `blps_penalty` (penalty-service)

**Инварианты:**

- cross-DB joins **запрещены**
- foreign keys между схемами разных сервисов **запрещены**
- идентификаторы из чужого сервиса хранятся как обычные `Long` (например, `claims.landlord_id`)
- межсервисная связность — через `userId`, `claimId`, `correlationId`, `sagaId`

Каждая БД содержит две системные таблицы (`platform-core` контракт):

- `outbox_events` — исходящие события для relay'а
- `processed_messages` — Inbox дедупликация входящих событий

## 8. Consistency модель

| Граница | Гарантия |
|---|---|
| Внутри одного сервиса | strong consistency (ACID Postgres + `@Transactional`) |
| Между сервисами | **eventual consistency** через async Kafka |
| Outbox + Kafka publish | at-least-once (дубликаты возможны) |
| Inbox dedup | at-most-once side effect (атомарно с бизнес-update) |

**Окна неконсистентности — спроектированы явно** через intermediate-статусы:

- `ASSESSMENT_IN_PROGRESS` — claim создан, assessment ещё не завершён
- `PENALTY_PROCESSING` — admin принял решение, penalty ещё применяется
- `PENALTY_PROCESSING_FAILED` — penalty упал, ждёт manual retry

Long-running операции через API возвращают **HTTP 202** + `processingStatus` + `correlationId`. Клиент опрашивает `GET /api/claims/{id}/process-status`.

## 9. Saga choreography

Нет центрального orchestrator engine. Каждый сервис:

1. реагирует на доменное событие
2. выполняет локальную транзакцию
3. публикует следующее доменное событие через outbox

Идентификация саг через `sagaId`:

| sagaId | Участники | Что демонстрирует |
|---|---|---|
| `claim-lifecycle-{claimId}` | claim-service (self) | local saga, intermediate states |
| `penalty-application-{claimId}` | claim-service → penalty-service → claim-service + edge-service | межсервисная saga с non-rollbackable step |
| `user-deactivation-{userId}` | edge-service → claim-service | distributed reaction на бизнес-факт |
| `attachment-binding-{claimId}` | claim-service ↔ MinIO | non-XA участник во внешнем хранилище |

Состояние саги отражено в **бизнес-статусах** aggregate (`claims.status`, `penalty_operations.status`) и в `claim_timeline`. Отдельной таблицы `saga_state` нет.

## 10. Масштабирование

### Горизонтально масштабируется

| Сервис | Условия |
|---|---|
| `edge-service` | stateless, JWT валидируется по shared secret; БД shared между репликами |
| `claim-service` | stateless application layer; конкурентные update'ы защищены `@Version` (optimistic locking); event-обработка через Kafka consumer group |
| `penalty-service` | worker через Kafka consumer group, sharding по `claimId` partition key |

### Bottlenecks текущего demo

- один Kafka broker (no replication factor > 1)
- один ZooKeeper
- одна Postgres на сервис без HA / read replicas
- сервисы запускаются вне контейнеров (`./gradlew :…:bootRun`)

Масштабирование **архитектурно заложено**, но production-hardening не сделан и не входит в scope лабы.

## 11. Что НЕ входит в архитектуру (явно)

Чтобы не плодить ложных ожиданий:

- distributed tracing stack (OpenTelemetry / Jaeger) — только `correlationId` в MDC + logs
- metrics stack (Prometheus / Grafana)
- centralized logs (ELK / Loki)
- schema registry (Avro / Protobuf for Kafka)
- DLT monitoring UI / alerting
- production OAuth2 / service-to-service mTLS — demo JWT (HS256)
- service mesh (Istio / Linkerd)
- Kubernetes / GitOps
- multi-region HA

Эти штуки — **future work**, не сегодняшний scope.

## 12. Почему именно 3 сервиса (а не больше и не меньше)

**Почему не 1 (монолит):** требование лабы — продемонстрировать distributed transactions, что невозможно в одной БД.

**Почему не 8 (как раньше):** `assessment`, `notification`, `audit`, `storage` — это не bounded contexts, а слои/реакции внутри claim-контекста. Их выделение в отдельные сервисы создавало over-decomposition: лишние процессы, сети, БД без реальной разницы в data ownership.

**Почему именно эти 3:**

- `edge-service` отделён по causa-`identity` — он source of truth для users и контролирует периметр. Не может быть слит с `claim-service` без нарушения single-responsibility.
- `claim-service` — центральный domain-сервис, владеет всем что про конкретную заявку.
- `penalty-service` — единственный контекст с **необратимым внешним side effect**. Обязан быть отдельным процессом, чтобы продемонстрировать saga с non-rollbackable шагом, manual recovery и компенсацию-как-бизнес-действие.

Это минимальная декомпозиция, при которой:

- сохраняется database-per-service
- все ключевые distributed patterns (Outbox, Inbox, Saga, Idempotency, Eventual Consistency, MinIO outside XA, manual recovery) — реально нужны и работают на этих границах
- ничего не выделено искусственно
