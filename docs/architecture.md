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
- `event-driven integration`
- `saga choreography`
- `eventual consistency`
- `read-side projections` для audit/notification

Это не fully isolated cloud-native production platform, а учебная и демонстрационная реализация System Design.

## 3. Модульная структура репозитория

### Общий модуль

- `platform-core`

Содержит:

- `EventType`
- `EventEnvelope`
- `TopicNames`
- payload classes
- `OutboxService`
- `OutboxRelay`
- `ProcessedMessageService`
- общий `ApiExceptionHandler`

### Бизнес-сервисы

- `auth-service`
- `claim-service`
- `assessment-service`
- `penalty-service`
- `notification-service`
- `audit-service`

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

### Синхронные вызовы

Сейчас в системе есть один важный sync path:

- `claim-service -> auth-service` через `GET /internal/users/{id}`

Этот вызов нужен для:

- проверки существования пользователя
- проверки роли
- проверки, что пользователь не деактивирован

### Асинхронные вызовы

Межсервисные бизнес-события идут через Kafka:

- `claim.events`
- `assessment.events`
- `penalty.events`
- `auth.events`

`notification-service` и `audit-service` подписаны на все доменные топики.

## 6. Матрица ответственности по сервисам

| Сервис | Владеет данными | Пишет в Kafka | Читает из Kafka | Публичный REST |
| --- | --- | --- | --- | --- |
| `auth-service` | `users` | `USER_DEACTIVATED` | `PENALTY_APPLIED` | да |
| `claim-service` | `claims`, `claim_timeline` | `CLAIM_CREATED`, `ADDITIONAL_INFO_PROVIDED`, `TENANT_RESPONSE_RECEIVED`, `CLAIM_CLOSED_NO_PENALTY`, `PENALTY_APPLICATION_REQUESTED` | `ASSESSMENT_COMPLETED`, `PENALTY_APPLIED`, `PENALTY_APPLICATION_FAILED`, `USER_DEACTIVATED` | да |
| `assessment-service` | `assessment_jobs` | `ASSESSMENT_COMPLETED` | `CLAIM_CREATED`, `ADDITIONAL_INFO_PROVIDED` | нет |
| `penalty-service` | `penalty_operations` | `PENALTY_APPLIED`, `PENALTY_APPLICATION_FAILED` | `PENALTY_APPLICATION_REQUESTED` | да |
| `notification-service` | `notification_log` | нет | все доменные события | нет |
| `audit-service` | `audit_records` | нет | все доменные события | да |

## 7. Топология данных

У каждого сервиса своя физическая БД:

- `blps_auth`
- `blps_claim`
- `blps_assessment`
- `blps_penalty`
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
- `notification-service`
- `audit-service`

Почему:

- состояние хранится в БД
- сервисы stateless на уровне HTTP/API
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

- outbox
- inbox/idempotency
- choreography saga
- manual retry для penalty flow
- read-side audit trail

### Пока не реализовано

- API gateway
- distributed tracing stack
- metrics/Prometheus/Grafana
- DLQ/retry topics
- schema registry / contract evolution policy
- compensating workers общего назначения
- security perimeter между сервисами

## 11. Почему это хороший учебный System Design

Проект наглядно показывает:

- как декомпозировать процесс по владельцам данных
- почему XA между микросервисами плохо масштабируется
- как решать `DB + message broker` dual write problem
- как организовать бизнес-процесс без централизованного workflow engine
- как проектировать явные промежуточные состояния вместо "магического мгновенного commit everywhere"
