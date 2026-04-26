BEGIN;

-- =====================================================================
-- PENALTY SERVICE DB
-- Async penalty operations + shared distributed-transaction tables.
-- Intended for database: blps_penalty
-- =====================================================================

CREATE TYPE penalty_operation_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'APPLIED',
    'FAILED'
);

CREATE TYPE outbox_event_type AS ENUM (
    'CLAIM_CREATED',
    'ADDITIONAL_INFO_PROVIDED',
    'ASSESSMENT_COMPLETED',
    'ASSESSMENT_FAILED',
    'TENANT_RESPONSE_RECEIVED',
    'TENANT_RESPONSE_EXPIRED',
    'CLAIM_CLOSED_NO_PENALTY',
    'PENALTY_APPLICATION_REQUESTED',
    'PENALTY_APPLIED',
    'PENALTY_APPLICATION_FAILED',
    'USER_DEACTIVATED',
    'ATTACHMENT_INITIALIZED',
    'ATTACHMENT_CONFIRMED',
    'ATTACHMENT_BINDING_REQUESTED',
    'ATTACHMENT_BOUND',
    'ATTACHMENT_BINDING_FAILED'
);

CREATE TYPE outbox_status AS ENUM (
    'NEW',
    'PUBLISHED',
    'FAILED',
    'DEAD'
);

CREATE TABLE penalty_operations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    correlation_id TEXT NOT NULL,
    penalty_amount NUMERIC(12, 2) NOT NULL CHECK (penalty_amount >= 0),
    penalty_currency CHAR(3) NOT NULL,
    simulate_failure BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    status penalty_operation_status NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);

CREATE TABLE outbox_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id TEXT NOT NULL UNIQUE,
    event_type outbox_event_type NOT NULL,
    topic_name TEXT NOT NULL,
    event_key TEXT NOT NULL,
    aggregate_type TEXT NOT NULL,
    aggregate_id TEXT NOT NULL,
    correlation_id TEXT NOT NULL,
    saga_id TEXT NOT NULL,
    actor_id BIGINT,
    payload_json TEXT NOT NULL,
    status outbox_status NOT NULL DEFAULT 'NEW',
    retry_count INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE TABLE processed_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id TEXT NOT NULL,
    consumer_name TEXT NOT NULL,
    correlation_id TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_processed_message_penalty UNIQUE (event_id, consumer_name)
);

CREATE INDEX idx_penalty_operations_status_created_at ON penalty_operations (status, created_at);
CREATE INDEX idx_penalty_operations_claim_id ON penalty_operations (claim_id);
CREATE INDEX idx_penalty_outbox_status_created_at ON outbox_events (status, created_at);
CREATE INDEX idx_penalty_processed_correlation_id ON processed_messages (correlation_id);

COMMIT;
