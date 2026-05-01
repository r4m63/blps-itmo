BEGIN;

-- =====================================================================
-- STORAGE SERVICE DB
-- Attachment metadata + shared distributed-transaction tables.
-- Intended for database: blps_storage
-- =====================================================================

CREATE TABLE attachments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    claim_id BIGINT,
    bucket_name TEXT NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    original_filename TEXT NOT NULL,
    content_type TEXT,
    status VARCHAR(30) NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
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
    CONSTRAINT uk_processed_message_storage UNIQUE (event_id, consumer_name)
);

CREATE INDEX idx_storage_attachments_owner ON attachments (owner_user_id);
CREATE INDEX idx_storage_attachments_claim_id ON attachments (claim_id);
CREATE INDEX idx_storage_attachments_status_updated_at ON attachments (status, updated_at);
CREATE INDEX idx_storage_outbox_status_created_at ON outbox_events (status, created_at);
CREATE INDEX idx_storage_processed_correlation_id ON processed_messages (correlation_id);

COMMIT;
