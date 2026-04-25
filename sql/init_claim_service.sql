BEGIN;

-- =====================================================================
-- CLAIM SERVICE DB
-- Claim workflow state + timeline + shared distributed-transaction tables.
-- Intended for database: blps_claim
-- =====================================================================

CREATE TYPE claim_status AS ENUM (
    'ASSESSMENT_IN_PROGRESS',
    'NEED_ADDITIONAL_INFO',
    'AWAITING_TENANT_RESPONSE',
    'SUPPORT_REVIEW',
    'PENALTY_PROCESSING',
    'PENALTY_APPLIED',
    'PENALTY_PROCESSING_FAILED',
    'CLOSED_NO_PENALTY'
);

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

CREATE TABLE claims (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correlation_id TEXT NOT NULL UNIQUE,
    landlord_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    status claim_status NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    claimed_amount NUMERIC(12, 2) NOT NULL CHECK (claimed_amount >= 0),
    currency CHAR(3) NOT NULL,
    assessment_amount NUMERIC(12, 2) CHECK (assessment_amount IS NULL OR assessment_amount >= 0),
    assessment_notes TEXT,
    penalty_amount NUMERIC(12, 2) CHECK (penalty_amount IS NULL OR penalty_amount >= 0),
    penalty_currency CHAR(3),
    resolution_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    CONSTRAINT chk_claim_participants_distinct CHECK (landlord_id <> tenant_id)
);

CREATE TABLE claim_timeline (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    from_status claim_status,
    to_status claim_status,
    actor_id BIGINT,
    note TEXT,
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
    CONSTRAINT uk_processed_message_claim UNIQUE (event_id, consumer_name)
);

CREATE INDEX idx_claims_status ON claims (status);
CREATE INDEX idx_claims_landlord_id ON claims (landlord_id);
CREATE INDEX idx_claims_tenant_id ON claims (tenant_id);
CREATE INDEX idx_claim_timeline_claim_id_created_at ON claim_timeline (claim_id, created_at);
CREATE INDEX idx_claim_outbox_status_created_at ON outbox_events (status, created_at);
CREATE INDEX idx_claim_processed_correlation_id ON processed_messages (correlation_id);

COMMIT;
