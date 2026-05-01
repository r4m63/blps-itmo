BEGIN;

-- =====================================================================
-- PENALTY SERVICE DB
-- Async penalty operations + shared distributed-transaction tables.
-- Intended for database: blps_penalty
-- =====================================================================

CREATE TABLE penalty_operations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    correlation_id TEXT NOT NULL,
    penalty_amount NUMERIC(12, 2) NOT NULL CHECK (penalty_amount >= 0),
    penalty_currency CHAR(3) NOT NULL,
    simulate_failure BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);

CREATE TABLE outbox_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id TEXT NOT NULL UNIQUE,
    event_type TEXT NOT NULL,
    topic_name TEXT NOT NULL,
    event_key TEXT NOT NULL,
    aggregate_type TEXT NOT NULL,
    aggregate_id TEXT NOT NULL,
    correlation_id TEXT NOT NULL,
    saga_id TEXT NOT NULL,
    actor_id BIGINT,
    payload_json TEXT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'NEW',
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
