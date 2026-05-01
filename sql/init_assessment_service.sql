BEGIN;

-- =====================================================================
-- ASSESSMENT SERVICE DB
-- Async assessment jobs + shared distributed-transaction tables.
-- Intended for database: blps_assessment
-- =====================================================================

CREATE TABLE assessment_jobs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    correlation_id TEXT NOT NULL,
    landlord_id BIGINT,
    tenant_id BIGINT,
    claimed_amount NUMERIC(12, 2) NOT NULL CHECK (claimed_amount >= 0),
    currency CHAR(3) NOT NULL,
    comment TEXT,
    attempt_no INT NOT NULL DEFAULT 1 CHECK (attempt_no >= 1),
    attachment_count INT NOT NULL DEFAULT 0 CHECK (attachment_count >= 0),
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
    CONSTRAINT uk_processed_message_assessment UNIQUE (event_id, consumer_name)
);

CREATE INDEX idx_assessment_jobs_status_created_at ON assessment_jobs (status, created_at);
CREATE INDEX idx_assessment_jobs_claim_id ON assessment_jobs (claim_id);
CREATE INDEX idx_assessment_outbox_status_created_at ON outbox_events (status, created_at);
CREATE INDEX idx_assessment_processed_correlation_id ON processed_messages (correlation_id);

COMMIT;
