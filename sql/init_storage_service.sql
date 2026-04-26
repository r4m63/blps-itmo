BEGIN;

-- =====================================================================
-- STORAGE SERVICE DB
-- Attachment metadata + shared distributed-transaction tables.
-- Intended for database: blps_storage
-- =====================================================================

CREATE TYPE attachment_status AS ENUM (
    'INITIALIZED',
    'CONFIRMED',
    'BINDING_REQUESTED',
    'BOUND',
    'BINDING_FAILED'
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

CREATE TABLE attachments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    claim_id BIGINT,
    bucket_name TEXT NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    original_filename TEXT NOT NULL,
    content_type TEXT,
    status attachment_status NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
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
    CONSTRAINT uk_processed_message_storage UNIQUE (event_id, consumer_name)
);

CREATE INDEX idx_storage_attachments_owner ON attachments (owner_user_id);
CREATE INDEX idx_storage_attachments_claim_id ON attachments (claim_id);
CREATE INDEX idx_storage_attachments_status_updated_at ON attachments (status, updated_at);
CREATE INDEX idx_storage_outbox_status_created_at ON outbox_events (status, created_at);
CREATE INDEX idx_storage_processed_correlation_id ON processed_messages (correlation_id);

COMMIT;
