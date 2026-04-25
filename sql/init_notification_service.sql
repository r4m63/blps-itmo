BEGIN;

-- =====================================================================
-- NOTIFICATION SERVICE DB
-- Notification log + shared distributed-transaction tables.
-- Intended for database: blps_notification
-- =====================================================================

CREATE TYPE outbox_event_type AS ENUM (
    'CLAIM_CREATED',
    'ADDITIONAL_INFO_PROVIDED',
    'ASSESSMENT_COMPLETED',
    'TENANT_RESPONSE_RECEIVED',
    'CLAIM_CLOSED_NO_PENALTY',
    'PENALTY_APPLICATION_REQUESTED',
    'PENALTY_APPLIED',
    'PENALTY_APPLICATION_FAILED',
    'USER_DEACTIVATED'
);

CREATE TYPE outbox_status AS ENUM (
    'NEW',
    'PUBLISHED',
    'FAILED'
);

CREATE TABLE notification_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    aggregate_id TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
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
    CONSTRAINT uk_processed_message_notification UNIQUE (event_id, consumer_name)
);

CREATE INDEX idx_notification_log_event_id ON notification_log (event_id);
CREATE INDEX idx_notification_log_aggregate_id ON notification_log (aggregate_id);
CREATE INDEX idx_notification_outbox_status_created_at ON outbox_events (status, created_at);
CREATE INDEX idx_notification_processed_correlation_id ON processed_messages (correlation_id);

COMMIT;
