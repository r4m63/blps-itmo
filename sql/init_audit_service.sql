BEGIN;

-- =====================================================================
-- AUDIT SERVICE DB
-- Audit trail + shared distributed-transaction tables.
-- Intended for database: blps_audit
-- =====================================================================

CREATE TABLE audit_records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    aggregate_type TEXT NOT NULL,
    aggregate_id TEXT NOT NULL,
    correlation_id TEXT NOT NULL,
    saga_id TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
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
    CONSTRAINT uk_processed_message_audit UNIQUE (event_id, consumer_name)
);

CREATE INDEX idx_audit_records_aggregate ON audit_records (aggregate_type, aggregate_id);
CREATE INDEX idx_audit_records_correlation_id ON audit_records (correlation_id);
CREATE INDEX idx_audit_records_saga_id ON audit_records (saga_id);
CREATE INDEX idx_audit_outbox_status_created_at ON outbox_events (status, created_at);
CREATE INDEX idx_audit_processed_correlation_id ON processed_messages (correlation_id);

COMMIT;
