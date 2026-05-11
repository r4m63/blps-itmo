# Data Model and Data Ownership

## 1. Главный инвариант

Каждый сервис владеет только своей схемой и своей физической БД:

- `blps_identity` — `edge-service`
- `blps_claim` — `claim-service`
- `blps_penalty` — `penalty-service`

**Правила:**

- чужие таблицы напрямую не читаются (ни через JOIN, ни через JPA)
- cross-service ORM relationships **отсутствуют**
- foreign keys между схемами разных сервисов **запрещены**
- идентификаторы из чужого сервиса хранятся как обычные `Long`, не как entity reference
- интеграция — только через Kafka events и точечные sync gRPC вызовы

## 2. Общие инфраструктурные таблицы (в каждой service-БД)

### `outbox_events`

```sql
CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL UNIQUE,
    event_type      VARCHAR(64) NOT NULL,
    topic_name      VARCHAR(128) NOT NULL,
    event_key       VARCHAR(128) NOT NULL,
    aggregate_type  VARCHAR(32) NOT NULL,
    aggregate_id    VARCHAR(64) NOT NULL,
    correlation_id  VARCHAR(64),
    saga_id         VARCHAR(128),
    actor_id        BIGINT,
    payload_json    JSONB NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'NEW',
    retry_count     INT NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status_created ON outbox_events (status, created_at) WHERE status IN ('NEW', 'FAILED');
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);
```

**Назначение:** гарантированная публикация доменных событий после локального commit'а.

### `processed_messages`

```sql
CREATE TABLE processed_messages (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL,
    consumer_name   VARCHAR(128) NOT NULL,
    correlation_id  VARCHAR(64),
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (event_id, consumer_name)
);

CREATE INDEX idx_processed_event ON processed_messages (event_id);
```

**Назначение:** Inbox-дедупликация входящих Kafka событий. Запись делается атомарно с бизнес-update.

---

## 3. `edge-service` БД: `blps_identity`

### Таблица `users`

```sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    role            VARCHAR(16) NOT NULL CHECK (role IN ('LANDLORD', 'TENANT', 'SUPPORT', 'ADMIN')),
    enabled         BOOLEAN NOT NULL DEFAULT true,
    penalty_count   INT NOT NULL DEFAULT 0 CHECK (penalty_count >= 0),
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Authoritative source** для:

- ролей пользователей
- enabled/disabled flag
- penalty_count

**Не содержит:**

- claims, penalties — это в других сервисах

### Инициальный seed (для demo)

6 пользователей:

| id | email | role |
|---|---|---|
| 1 | landlord1@example.com | LANDLORD |
| 2 | landlord2@example.com | LANDLORD |
| 3 | tenant1@example.com | TENANT |
| 4 | tenant2@example.com | TENANT |
| 5 | support@example.com | SUPPORT |
| 6 | admin@example.com | ADMIN |

---

## 4. `claim-service` БД: `blps_claim`

### Таблица `claims`

```sql
CREATE TABLE claims (
    id                  BIGSERIAL PRIMARY KEY,
    correlation_id      VARCHAR(64) NOT NULL,
    landlord_id         BIGINT NOT NULL,
    tenant_id           BIGINT NOT NULL,
    status              VARCHAR(32) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    claimed_amount      NUMERIC(15, 2) NOT NULL CHECK (claimed_amount > 0),
    currency            CHAR(3) NOT NULL,
    assessment_amount   NUMERIC(15, 2),
    assessment_notes    TEXT,
    penalty_amount      NUMERIC(15, 2),
    penalty_currency    CHAR(3),
    resolution_note     TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at           TIMESTAMPTZ,

    CHECK (landlord_id <> tenant_id),
    CHECK (status IN (
      'ASSESSMENT_IN_PROGRESS', 'NEED_ADDITIONAL_INFO', 'AWAITING_TENANT_RESPONSE',
      'SUPPORT_REVIEW', 'PENALTY_PROCESSING', 'PENALTY_APPLIED',
      'PENALTY_PROCESSING_FAILED', 'CLOSED_NO_PENALTY'
    ))
);

CREATE INDEX idx_claims_status ON claims (status) WHERE closed_at IS NULL;
CREATE INDEX idx_claims_landlord ON claims (landlord_id);
CREATE INDEX idx_claims_tenant ON claims (tenant_id);
CREATE INDEX idx_claims_tenant_timeout ON claims (status, updated_at)
    WHERE status = 'AWAITING_TENANT_RESPONSE';
```

**Замечания:**

- `landlord_id`/`tenant_id` — обычные `BIGINT`, **без** foreign key в `users` (другой сервис)
- `version` — optimistic locking через JPA `@Version`
- partial index на `idx_claims_tenant_timeout` ускоряет scheduled job для P3D timeout

### Таблица `claim_timeline`

```sql
CREATE TABLE claim_timeline (
    id              BIGSERIAL PRIMARY KEY,
    claim_id        BIGINT NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    event_type      VARCHAR(64) NOT NULL,
    from_status     VARCHAR(32),
    to_status       VARCHAR(32),
    actor_id        BIGINT,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_timeline_claim ON claim_timeline (claim_id, created_at);
```

**Назначение:** business timeline / event trail внутри claim-service. Заменяет отдельный audit-service.

### Таблица `attachments`

```sql
CREATE TABLE attachments (
    id                  BIGSERIAL PRIMARY KEY,
    object_key          UUID NOT NULL UNIQUE,
    owner_user_id       BIGINT NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(128),
    size_bytes          BIGINT,
    status              VARCHAR(16) NOT NULL CHECK (status IN ('INITIALIZED', 'CONFIRMED', 'BOUND', 'ORPHANED')),
    claim_id            BIGINT REFERENCES claims(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at        TIMESTAMPTZ,
    bound_at            TIMESTAMPTZ
);

CREATE INDEX idx_attachments_claim ON attachments (claim_id) WHERE claim_id IS NOT NULL;
CREATE INDEX idx_attachments_orphan_candidates ON attachments (status, created_at)
    WHERE status IN ('INITIALIZED', 'CONFIRMED') AND claim_id IS NULL;
```

**MinIO как non-XA участник:**

- метаданные хранятся здесь
- бинарные данные — в MinIO bucket `attachments`, ключ = `object_key`
- saga: `INITIALIZED → CONFIRMED → BOUND`, либо → `ORPHANED` через reconciliation job

### Таблица `assessment_jobs`

```sql
CREATE TABLE assessment_jobs (
    id              BIGSERIAL PRIMARY KEY,
    claim_id        BIGINT NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    correlation_id  VARCHAR(64) NOT NULL,
    attempt_no      INT NOT NULL DEFAULT 1,
    status          VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    claimed_amount  NUMERIC(15, 2) NOT NULL,
    currency        CHAR(3) NOT NULL,
    comment         TEXT,
    result_json     JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,
    error_message   TEXT
);

CREATE INDEX idx_assessment_pending ON assessment_jobs (status, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
```

**Назначение:** очередь и журнал async-обработки assessment'ов. Worker (`@Scheduled` в claim-service) забирает `PENDING` jobs.

### Таблица `notifications`

```sql
CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    claim_id        BIGINT REFERENCES claims(id) ON DELETE CASCADE,
    recipient_user_id BIGINT NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    title           VARCHAR(255),
    body            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_user_id, created_at DESC);
CREATE INDEX idx_notifications_claim ON notifications (claim_id);
```

**Назначение:** write-side log уведомлений. Реальная отправка email/push в demo не реализована.

---

## 5. `penalty-service` БД: `blps_penalty`

### Таблица `penalty_operations`

```sql
CREATE TABLE penalty_operations (
    id                  BIGSERIAL PRIMARY KEY,
    claim_id            BIGINT NOT NULL,
    tenant_id           BIGINT NOT NULL,
    correlation_id      VARCHAR(64) NOT NULL,
    saga_id             VARCHAR(128) NOT NULL,
    penalty_amount      NUMERIC(15, 2) NOT NULL CHECK (penalty_amount > 0),
    penalty_currency    CHAR(3) NOT NULL,
    simulate_failure    BOOLEAN NOT NULL DEFAULT false,
    status              VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'APPLIED', 'FAILED')),
    reason              TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    attempt_no          INT NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ
);

CREATE INDEX idx_penalty_pending ON penalty_operations (status, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_penalty_claim ON penalty_operations (claim_id);
```

**Замечания:**

- `claim_id` — обычный `BIGINT`, **без** FK на `claims` (другой сервис)
- `attempt_no` инкрементируется при retry FAILED operation
- `version` — optimistic locking
- partial index ускоряет worker poll

---

## 6. Инварианты данных

### Claim aggregate

- `landlord_id <> tenant_id` (CHECK constraint)
- claim не переходит в финальный статус без записи в `claim_timeline` (бизнес-инвариант, не SQL)
- `penalty_amount > 0` если `applyPenalty=true` (валидация на уровне сервиса)
- `closed_at` non-null только для терминальных статусов (`PENALTY_APPLIED`, `CLOSED_NO_PENALTY`)

### Identity aggregate

- `penalty_count >= 0` (CHECK constraint)
- деактивированный пользователь не должен проходить в claim-service как актор (валидация в `IdentityRpcService.GetUser` через флаг `enabled`)

### Penalty aggregate

- `penalty_operations` проходят через явные intermediate статусы (`PENDING → PROCESSING → APPLIED|FAILED`)
- retry FAILED operations возвращает их в `PENDING`, `attempt_no++`

### Async workers

- `assessment_jobs` и `penalty_operations` обрабатываются идемпотентно через `processed_messages` на consumer'ах + optimistic lock на самих job-таблицах

---

## 7. Что НЕ хранится централизованно

В системе **нет**:

- общей "global transaction" таблицы
- "saga state" таблицы
- shared user / role таблицы

Состояние процесса размазано по:

- основной статус aggregate в owning сервисе (`claims.status`, `penalty_operations.status`, `users.enabled`)
- локальное состояние worker'ов (`assessment_jobs`)
- следы интеграции в `outbox_events` + `processed_messages`
- бизнес-трейс в `claim_timeline`

Это нормально для event-driven архитектуры. Глобальный view системы при необходимости собирается через Kafka log + audit query на claim_timeline.

---

## 8. Миграции

Используется **Flyway** для каждого сервиса. Расположение:

```
services/edge-service/src/main/resources/db/migration/
  V1__init_identity.sql
  V2__seed_demo_users.sql

services/claim-service/src/main/resources/db/migration/
  V1__init_claim.sql
  V2__init_attachments.sql
  V3__init_assessment_jobs.sql
  V4__init_notifications.sql

services/penalty-service/src/main/resources/db/migration/
  V1__init_penalty.sql
```

**Inline `sql/init_*.sql` скрипты в корне проекта удаляются** — Flyway это единственный источник истины для схемы.

`spring.jpa.hibernate.ddl-auto=validate` (не `update`!) — Hibernate проверяет соответствие entity ↔ схема, но **не** меняет схему. Это форсит дисциплину миграций.

---

## 9. Backups и retention

Не в scope demo. Для prod-варианта:

- `outbox_events` published старше 30 дней можно архивировать в Postgres COLD partition или удалять
- `processed_messages` — то же, retention 30-90 дней (от Kafka retention)
- `claim_timeline` — keep forever или партиционировать по году
- WAL backup в S3, point-in-time recovery
