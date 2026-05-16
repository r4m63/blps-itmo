BEGIN;

-- =====================================================================
-- CLAIM-SERVICE DATABASE
-- Claim lifecycle: claims, messages, status history, attachments.
-- landlord_id / tenant_id / admin_reviewer_id / uploaded_by are INT
-- references to auth-service users. No cross-DB FKs.
-- Script is idempotent: wipes existing objects before recreating.
-- =====================================================================

DROP TABLE IF EXISTS saga_instances CASCADE;
DROP TABLE IF EXISTS claim_attachments CASCADE;
DROP TABLE IF EXISTS claim_status_history CASCADE;
DROP TABLE IF EXISTS claim_messages CASCADE;
DROP TABLE IF EXISTS claims CASCADE;
DROP TABLE IF EXISTS outbox_events CASCADE;
DROP TABLE IF EXISTS processed_messages CASCADE;
DROP TYPE  IF EXISTS sagatype;
DROP TYPE  IF EXISTS sagastate;
DROP TYPE  IF EXISTS attachmentpurpose;
DROP TYPE  IF EXISTS commenttype;
DROP TYPE  IF EXISTS claimstatus;

CREATE TYPE claimstatus AS ENUM (
    'SUBMITTED',
    'INTAKE_REVIEW',
    'NEED_ADDITIONAL_INFO',
    'UNDER_ASSESSMENT',
    'AWAITING_TENANT_RESPONSE',
    'SUPPORT_REVIEW',
    'PENALTY_PROCESSING',
    'PENALTY_APPLIED',
    'PENALTY_PROCESSING_FAILED',
    'CLOSED_NO_PENALTY'
);

CREATE TYPE commenttype AS ENUM (
    'LANDLORD_STATEMENT',
    'ADMIN_NOTE',
    'TENANT_RESPONSE',
    'ADDITIONAL_INFO_REQUEST',
    'ADDITIONAL_INFO_REPLY'
);

CREATE TYPE attachmentpurpose AS ENUM (
    'DAMAGE_EVIDENCE',
    'ADDITIONAL_MATERIAL',
    'SYSTEM'
);

CREATE TYPE sagatype AS ENUM (
    'PENALTY_APPLICATION'
);

CREATE TYPE sagastate AS ENUM (
    'STARTED',
    'AWAITING_PENALTY_APPLIED',
    'AWAITING_PENALTY_COUNTED',
    'COMPLETED',
    'PENALTY_FAILED',
    'COMPENSATING_REVOKE',
    'COMPENSATED',
    'COMPENSATION_FAILED'
);

CREATE TABLE claims (
    id                INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    landlord_id       INT NOT NULL,
    tenant_id         INT NOT NULL,
    status            claimstatus NOT NULL DEFAULT 'SUBMITTED',
    title             TEXT NOT NULL,
    description       TEXT NOT NULL,
    claimed_amount    NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (claimed_amount >= 0),
    currency          VARCHAR(3) NOT NULL DEFAULT 'USD',
    assessment_amount NUMERIC(12,2) CHECK (assessment_amount >= 0),
    assessment_notes  TEXT,
    admin_reviewer_id INT,
    resolution_note   TEXT,
    decided_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at         TIMESTAMPTZ,
    current_saga_id   UUID,
    CONSTRAINT chk_claim_parties_distinct CHECK (landlord_id <> tenant_id)
);

CREATE INDEX idx_claims_current_saga ON claims (current_saga_id);

CREATE TABLE claim_messages (
    id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id     INT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    user_id      INT NOT NULL,
    message_type commenttype NOT NULL,
    body         TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE claim_status_history (
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id    INT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    from_status claimstatus,
    to_status   claimstatus NOT NULL,
    actor_id    INT,
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE claim_attachments (
    id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id     INT REFERENCES claims (id) ON DELETE CASCADE,
    message_id   INT REFERENCES claim_messages (id) ON DELETE SET NULL,
    uploaded_by  INT NOT NULL,
    purpose      attachmentpurpose NOT NULL DEFAULT 'DAMAGE_EVIDENCE',
    object_key   TEXT NOT NULL UNIQUE,
    file_name    TEXT NOT NULL,
    content_type TEXT,
    size_bytes   BIGINT CHECK (size_bytes >= 0),
    uploaded     BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id   TEXT NOT NULL,
    event_type     TEXT NOT NULL,
    topic          TEXT NOT NULL,
    payload        JSONB NOT NULL,
    status         TEXT NOT NULL DEFAULT 'NEW',
    retry_count    INT NOT NULL DEFAULT 0,
    last_error     TEXT,
    saga_id        UUID,
    correlation_id UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_status_created ON outbox_events (status, created_at);
CREATE INDEX idx_outbox_events_saga ON outbox_events (saga_id);

CREATE TABLE processed_messages (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE saga_instances (
    saga_id        UUID PRIMARY KEY,
    saga_type      sagatype    NOT NULL DEFAULT 'PENALTY_APPLICATION',
    claim_id       INT         NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    state          sagastate   NOT NULL,
    attempt_count  INT         NOT NULL DEFAULT 0,
    max_attempts   INT         NOT NULL DEFAULT 3,
    failure_reason TEXT,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_event_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_saga_claim ON saga_instances (claim_id);
CREATE INDEX idx_saga_state_lastevent ON saga_instances (state, last_event_at)
    WHERE state IN ('AWAITING_PENALTY_APPLIED','AWAITING_PENALTY_COUNTED','COMPENSATING_REVOKE');

-- =====================================================================
-- Test claims (landlord_id=2, tenant_id=3 — see init_auth_service.sql)
-- =====================================================================

-- Fresh SUBMITTED claim, awaiting intake
INSERT INTO claims (landlord_id, tenant_id, status, title, description, claimed_amount, currency)
VALUES (2, 3, 'SUBMITTED', 'Broken sofa and stained carpet',
        'Tenant left visible damage; landlord requests assessment.', 250.00, 'USD');

-- Closed claim with penalty applied
INSERT INTO claims (landlord_id, tenant_id, status, title, description,
                    claimed_amount, currency, assessment_amount, assessment_notes,
                    admin_reviewer_id, resolution_note, decided_at, closed_at)
VALUES (2, 3, 'PENALTY_APPLIED', 'Past unpaid utilities',
        'Closed example: penalty already applied for unpaid utilities.',
        180.00, 'USD', 150.00, 'Confirmed underpayment.',
        1, 'Penalty applied per agreement.', now() - INTERVAL '1 day', now() - INTERVAL '1 day');

-- Closed without penalty
INSERT INTO claims (landlord_id, tenant_id, status, title, description,
                    claimed_amount, currency, assessment_amount, assessment_notes,
                    admin_reviewer_id, resolution_note, decided_at, closed_at)
VALUES (2, 3, 'CLOSED_NO_PENALTY', 'No-damage walkthrough',
        'Closed example: walkthrough revealed no damages.',
        50.00, 'USD', 0.00, 'No damage observed.',
        1, 'Closed without penalty.', now() - INTERVAL '2 days', now() - INTERVAL '2 days');

INSERT INTO claim_status_history (claim_id, from_status, to_status, actor_id, note) VALUES
    (1, NULL,                       'SUBMITTED',                2, 'Submitted by landlord'),
    (2, NULL,                       'SUBMITTED',                2, 'Submitted by landlord'),
    (2, 'SUBMITTED',                'INTAKE_REVIEW',            1, 'Admin took for review'),
    (2, 'INTAKE_REVIEW',            'UNDER_ASSESSMENT',         1, 'Sufficient info provided'),
    (2, 'UNDER_ASSESSMENT',         'AWAITING_TENANT_RESPONSE', 1, 'Assessment recorded'),
    (2, 'AWAITING_TENANT_RESPONSE', 'SUPPORT_REVIEW',           3, 'Tenant responded'),
    (2, 'SUPPORT_REVIEW',           'PENALTY_PROCESSING',       1, 'Support approved penalty'),
    (2, 'PENALTY_PROCESSING',       'PENALTY_APPLIED',          1, 'Penalty applied'),
    (3, NULL,                       'SUBMITTED',                2, 'Submitted by landlord'),
    (3, 'SUBMITTED',                'INTAKE_REVIEW',            1, 'Admin took for review'),
    (3, 'INTAKE_REVIEW',            'UNDER_ASSESSMENT',         1, 'Sufficient info'),
    (3, 'UNDER_ASSESSMENT',         'AWAITING_TENANT_RESPONSE', 1, 'No damage assessed'),
    (3, 'AWAITING_TENANT_RESPONSE', 'SUPPORT_REVIEW',           3, 'Tenant agreed'),
    (3, 'SUPPORT_REVIEW',           'CLOSED_NO_PENALTY',        1, 'Closed without penalty');

INSERT INTO claim_messages (claim_id, user_id, message_type, body) VALUES
    (2, 2, 'LANDLORD_STATEMENT', 'Documented evidence attached.'),
    (2, 1, 'ADMIN_NOTE',         'Reviewed and approved.'),
    (2, 3, 'TENANT_RESPONSE',    'I accept the assessment.');

COMMIT;
