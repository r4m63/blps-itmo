# System Design и Архитектура

## 1. Цели архитектуры

Архитектура проекта строилась под три цели:

1. Разделить систему на независимые bounded contexts с отдельными БД.
2. Убрать зависимость от глобальных распределённых ACID-транзакций.
3. Показать практический event-driven flow с асинхронными задачами и явной обработкой частичных отказов.

## 2. Архитектурный стиль

Текущий стиль системы:

- `microservices`
- `database per service`
- `HTTP API gateway`
- `gRPC` для синхронных внутренних вызовов
- `event-driven integration`
- `saga choreography`
- `eventual consistency`
- `read-side projections` для audit/notification

Это не fully isolated cloud-native production platform, а учебная и демонстрационная реализация System Design.

## 3. Модульная структура репозитория

### Общий модуль

- `platform-core`
- `grpc-contracts`

Содержит:

- `EventType`
- `EventEnvelope`
- `TopicNames`
- payload classes
- `OutboxService`
- `OutboxRelay`
- `ProcessedMessageService`
- общий `ApiExceptionHandler`
- protobuf-контракты и generated gRPC stubs
- lifecycle для gRPC servers и фабрику gRPC clients

### Бизнес-сервисы

- `auth-service`
- `claim-service`
- `assessment-service`
- `penalty-service`
- `storage-service`
- `notification-service`
- `audit-service`

Физически сервисы лежат в каталоге `services/`, а общие модули вынесены в `lib/`.

## 4. Bounded Contexts

### `auth-service`

Контекст идентичности и статуса пользователя.

Отвечает за:

- список пользователей
- роль (`LANDLORD`, `TENANT`, `ADMIN`)
- `enabled/disabled`
- `penaltyCount`

Не отвечает за:

- хранение заявок
- хранение решений по claim
- управление статусами заявки

### `claim-service`

Центральный бизнес-контекст процесса.

Отвечает за:

- создание claim
- claim state machine
- claim timeline
- перевод заявки между состояниями
- реакцию на assessment / penalty / user deactivation events

### `assessment-service`

Выделенный async worker-контекст.

Отвечает за:

- создание assessment job
- асинхронную обработку заявки
- публикацию результата оценки

### `storage-service`

Контекст attachment metadata и MinIO object-key lifecycle.

Отвечает за:

- инициализацию attachment metadata
- подтверждение загрузки объекта
- привязку attachments к claim через saga
- публикацию storage events

### `penalty-service`

Контекст применения штрафа.

Отвечает за:

- создание penalty operation
- имитацию долгого внешнего процессора
- публикацию `PENALTY_APPLIED` / `PENALTY_APPLICATION_FAILED`
- ручный retry failed operation

### `notification-service`

Read-side контекст уведомлений.

Отвечает за:

- materialized log уведомлений по событиям

### `audit-service`

Read-side контекст трассировки.

Отвечает за:

- персистентный event trail
- поиск событий по `claimId`
- демонстрацию end-to-end saga trace

## 5. Коммуникации

### Внешние HTTP вызовы

Клиенты не ходят напрямую в бизнес-сервисы. Внешняя точка входа:

- `api-gateway`
- HTTP `/api/**`
- demo JWT в `Authorization: Bearer ...`

Gateway валидирует токен, достаёт actor identity и вызывает backend-сервисы через gRPC.

### Синхронные внутренние вызовы

Все runtime sync path внутри системы оформляются через gRPC-контракты из `lib/grpc-contracts`.

Ключевой service-to-service path:

- `claim-service -> auth-service` через `AuthRpcService.GetUser`

Он нужен для:

- проверки существования пользователя
- проверки роли
- проверки, что пользователь не деактивирован

Gateway также использует gRPC stubs:

- `AuthRpcService`
- `ClaimRpcService`
- `PenaltyRpcService`
- `StorageRpcService`
- `AuditRpcService`

REST-контроллеры в backend-сервисах могут оставаться для прямого локального debug, но не являются контрактом межсервисной синхронной интеграции.

### Асинхронные вызовы

Межсервисные бизнес-события идут через Kafka:

- `claim.events`
- `assessment.events`
- `penalty.events`
- `auth.events`
- `storage.events`

`notification-service` и `audit-service` подписаны на все доменные топики.

## 6. Матрица ответственности по сервисам

| Сервис | Владеет данными | Пишет в Kafka | Читает из Kafka | Внешний доступ |
| --- | --- | --- | --- | --- |
| `api-gateway` | нет | нет | нет | HTTP edge |
| `auth-service` | `users` | `USER_DEACTIVATED` | `PENALTY_APPLIED` | через gateway/gRPC |
| `claim-service` | `claims`, `claim_timeline`, attachment refs | `CLAIM_CREATED`, `ADDITIONAL_INFO_PROVIDED`, `TENANT_RESPONSE_RECEIVED`, `TENANT_RESPONSE_EXPIRED`, `CLAIM_CLOSED_NO_PENALTY`, `PENALTY_APPLICATION_REQUESTED`, `ATTACHMENT_BINDING_REQUESTED` | `ASSESSMENT_COMPLETED`, `ASSESSMENT_FAILED`, `PENALTY_APPLIED`, `PENALTY_APPLICATION_FAILED`, `USER_DEACTIVATED`, `ATTACHMENT_BOUND`, `ATTACHMENT_BINDING_FAILED` | через gateway/gRPC |
| `assessment-service` | `assessment_jobs` | `ASSESSMENT_COMPLETED`, `ASSESSMENT_FAILED` | `CLAIM_CREATED`, `ADDITIONAL_INFO_PROVIDED` | нет |
| `penalty-service` | `penalty_operations` | `PENALTY_APPLIED`, `PENALTY_APPLICATION_FAILED` | `PENALTY_APPLICATION_REQUESTED` | через gateway/gRPC |
| `storage-service` | `attachments` | `ATTACHMENT_INITIALIZED`, `ATTACHMENT_CONFIRMED`, `ATTACHMENT_BOUND`, `ATTACHMENT_BINDING_FAILED` | `ATTACHMENT_BINDING_REQUESTED` | через gateway/gRPC |
| `notification-service` | `notification_log` | нет | все доменные события | нет |
| `audit-service` | `audit_records` | нет | все доменные события | через gateway/gRPC |

## 7. Топология данных

У каждого сервиса своя физическая БД:

- `blps_auth`
- `blps_claim`
- `blps_assessment`
- `blps_penalty`
- `blps_storage`
- `blps_notification`
- `blps_audit`

Инварианты:

- прямые cross-DB join запрещены
- foreign key между сервисами отсутствуют
- идентификаторы пользователей в `claim-service` хранятся как обычные `Long`, а не как ORM relation
- межсервисная связность достигается через `userId`, `claimId`, `correlationId`, `sagaId`

## 8. Консистентность

Модель консистентности:

- локально внутри сервиса: сильная консистентность
- между сервисами: eventual consistency

Примеры окон неконсистентности:

- claim уже создан, но assessment ещё не завершён
- penalty уже запрошен, но claim ещё не финализирован
- user уже деактивирован, но claim-service ещё не обработал это событие

Это не race-condition в понимании ошибки. Это ожидаемая часть distributed design.

## 9. Масштабирование

### Горизонтально масштабируются хорошо

- `claim-service`
- `auth-service`
- `storage-service`
- `notification-service`
- `audit-service`

Почему:

- состояние хранится в БД
- сервисы stateless на уровне HTTP/gRPC API
- dedup идёт через таблицы, а не через memory

### Масштабируются как async worker’ы

- `assessment-service`
- `penalty-service`

Почему:

- они читают сообщения из Kafka
- выполняют фоновую обработку
- используют consumer group semantics

Практический эффект:

- увеличение числа реплик даёт рост throughput, если хватает Kafka partitions и БД

## 10. Текущие ограничения дизайна

### Уже реализовано

- API gateway
- internal gRPC contracts and servers
- demo JWT/login/security perimeter
- outbox
- inbox/idempotency
- choreography saga
- manual retry для penalty flow
- DLT publishing through shared Kafka error handler
- timeout/reconciliation jobs for core async flows
- storage-service attachment saga
- read-side audit trail

### Пока не реализовано

- distributed tracing stack
- metrics/Prometheus/Grafana
- schema registry / contract evolution policy
- compensating workers общего назначения
- production-grade OAuth2/service-to-service auth

## 11. Почему это хороший учебный System Design

Проект наглядно показывает:

- как декомпозировать процесс по владельцам данных
- почему XA между микросервисами плохо масштабируется
- как решать `DB + message broker` dual write problem
- как организовать бизнес-процесс без централизованного workflow engine
- как проектировать явные промежуточные состояния вместо "магического мгновенного commit everywhere"
