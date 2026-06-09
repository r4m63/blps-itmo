BEGIN;

-- =====================================================================
-- PENALTY-SERVICE DATABASE
-- Penalty operations. claim_id / tenant_id / landlord_id / requested_by
-- are INT references to claim-service / auth-service. No cross-DB FKs.
-- Script is idempotent: wipes existing objects before recreating.
-- =====================================================================

DROP TABLE IF EXISTS penalty_operations CASCADE;
DROP TABLE IF EXISTS outbox_events CASCADE;
DROP TABLE IF EXISTS processed_messages CASCADE;
DROP TYPE  IF EXISTS penaltystatus;

CREATE TYPE penaltystatus AS ENUM (
    'PENDING',
    'PROCESSING',
    'APPLIED',
    'FAILED',
    'REVOKED'
);

CREATE TABLE penalty_operations (
    id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id       INT NOT NULL UNIQUE,
    tenant_id      INT NOT NULL,
    landlord_id    INT NOT NULL,
    requested_by   INT NOT NULL,
    amount         NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    currency       VARCHAR(3) NOT NULL DEFAULT 'USD',
    status         penaltystatus NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT,
    applied_at     TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    revoke_reason  TEXT,
    last_saga_id   UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_penalty_parties_distinct CHECK (landlord_id <> tenant_id)
);

CREATE INDEX idx_penalty_last_saga ON penalty_operations (last_saga_id);

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

-- =====================================================================
-- Test penalty for claim id=2 from init_claim_service.sql
-- requested_by=1 (admin), tenant=3, landlord=2
-- =====================================================================
INSERT INTO penalty_operations (claim_id, tenant_id, landlord_id, requested_by,
                                amount, currency, status, applied_at)
VALUES (2, 3, 2, 1, 150.00, 'USD', 'APPLIED', now() - INTERVAL '1 day');

COMMIT;
