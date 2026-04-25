# Data Model и Data Ownership

## 1. Главное правило

Каждый сервис владеет только своей схемой и своей БД.

Правила:

- чужие таблицы напрямую не читаются
- cross-service ORM relationships отсутствуют
- интеграция идёт через Kafka events и единичные sync API

## 2. Общие инфраструктурные таблицы

Каждая service DB содержит две системные таблицы:

### `outbox_events`

Назначение:

- гарантированная публикация доменных событий после локального commit

Основные поля:

- `event_id`
- `event_type`
- `topic_name`
- `event_key`
- `aggregate_type`
- `aggregate_id`
- `correlation_id`
- `saga_id`
- `actor_id`
- `payload_json`
- `status`
- `retry_count`
- `error_message`
- `created_at`
- `published_at`

### `processed_messages`

Назначение:

- дедупликация входящих сообщений

Основные поля:

- `event_id`
- `consumer_name`
- `correlation_id`
- `processed_at`

## 3. `auth-service` DB

SQL:

- [sql/init_auth_service.sql](../sql/init_auth_service.sql)

Основная таблица:

### `users`

Содержит:

- `id`
- `email`
- `role`
- `enabled`
- `penalty_count`
- `created_at`
- `updated_at`

Роль таблицы:

- authoritative source для ролей и состояния пользователя

## 4. `claim-service` DB

SQL:

- [sql/init_claim_service.sql](../sql/init_claim_service.sql)

### `claims`

Содержит:

- `correlation_id`
- `landlord_id`
- `tenant_id`
- `status`
- `title`
- `description`
- `claimed_amount`
- `currency`
- `assessment_amount`
- `assessment_notes`
- `penalty_amount`
- `penalty_currency`
- `resolution_note`
- `created_at`
- `updated_at`
- `closed_at`

Замечание:

- `landlord_id` и `tenant_id` — это просто идентификаторы из `auth-service`
- foreign key в `auth-db` нет и быть не должно

### `claim_timeline`

Содержит:

- `claim_id`
- `event_type`
- `from_status`
- `to_status`
- `actor_id`
- `note`
- `created_at`

Роль:

- business timeline и объяснимость переходов

## 5. `assessment-service` DB

SQL:

- [sql/init_assessment_service.sql](../sql/init_assessment_service.sql)

### `assessment_jobs`

Содержит:

- `claim_id`
- `correlation_id`
- `landlord_id`
- `tenant_id`
- `claimed_amount`
- `currency`
- `comment`
- `attempt_no`
- `status`
- `created_at`
- `processed_at`

Роль:

- очередь и журнал async assessment processing

## 6. `penalty-service` DB

SQL:

- [sql/init_penalty_service.sql](../sql/init_penalty_service.sql)

### `penalty_operations`

Содержит:

- `claim_id`
- `tenant_id`
- `correlation_id`
- `penalty_amount`
- `penalty_currency`
- `simulate_failure`
- `reason`
- `status`
- `created_at`
- `processed_at`

Роль:

- источник истины для выполнения penalty side effect

## 7. `notification-service` DB

SQL:

- [sql/init_notification_service.sql](../sql/init_notification_service.sql)

### `notification_log`

Содержит:

- `event_id`
- `event_type`
- `aggregate_id`
- `message`
- `created_at`

Роль:

- материализованный лог пользовательских и системных уведомлений

## 8. `audit-service` DB

SQL:

- [sql/init_audit_service.sql](../sql/init_audit_service.sql)

### `audit_records`

Содержит:

- `event_id`
- `event_type`
- `aggregate_type`
- `aggregate_id`
- `correlation_id`
- `saga_id`
- `payload_json`
- `created_at`

Роль:

- immutable audit trail для всех доменных событий

## 9. Инварианты данных

### Claim aggregate

- `landlord_id <> tenant_id`
- claim не может перейти в финальный статус без записи в timeline
- `penaltyAmount` должен быть положительным при `applyPenalty=true`

### Auth aggregate

- `penalty_count >= 0`
- деактивированный пользователь не должен проходить в `claim-service` в качестве валидного actor

### Async workers

- `assessment_jobs` и `penalty_operations` проходят через явные промежуточные статусы
- обработка событий идемпотентна через `processed_messages`

## 10. Что не хранится централизованно

В системе нет общей "global transaction table" или общей "business process state" таблицы.

Вместо этого состояние процесса размазано по нескольким уровням:

- основной статус хранится в `claims`
- локальное состояние worker’ов — в `assessment_jobs` и `penalty_operations`
- следы интеграции — в `outbox_events` и `processed_messages`
- трассировка — в `audit_records`

Это нормально для event-driven архитектуры.
