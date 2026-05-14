BEGIN;

-- =====================================================================
-- PENALTY-SERVICE DATABASE
-- Владеет операциями применения штрафа.
-- claim_id, tenant_id, landlord_id, requested_by — INT-ссылки на сущности
-- из других сервисов (claim-service / auth-service). Cross-DB FK
-- отсутствуют намеренно.
-- =====================================================================

CREATE TYPE penaltystatus AS ENUM (
    'PENDING',
    'PROCESSING',
    'APPLIED',
    'FAILED'
);

CREATE TABLE penalty_operations (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id INT NOT NULL,
    tenant_id INT NOT NULL,
    landlord_id INT NOT NULL,
    requested_by INT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status penaltystatus NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_penalty_parties_distinct CHECK (landlord_id <> tenant_id),
    CONSTRAINT chk_applied_at_present CHECK (
        status <> 'APPLIED'
        OR applied_at IS NOT NULL
    ),
    CONSTRAINT chk_failure_reason_present CHECK (
        status <> 'FAILED'
        OR failure_reason IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_penalty_operations_claim ON penalty_operations (claim_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    topic TEXT NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'NEW',
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_status_created ON outbox_events (status, created_at);

CREATE TABLE processed_messages (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
