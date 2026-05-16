-- =====================================================================
-- Idempotent migration: add saga infrastructure to claim-service DB.
-- Safe to run multiple times. Apply to blps_claim.
-- =====================================================================

BEGIN;

-- 1. Enums (Postgres doesn't have CREATE TYPE IF NOT EXISTS — use DO block)
DO $$ BEGIN
    CREATE TYPE sagatype AS ENUM ('PENALTY_APPLICATION');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
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
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 2. saga_instances table
CREATE TABLE IF NOT EXISTS saga_instances (
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

CREATE INDEX IF NOT EXISTS idx_saga_claim ON saga_instances (claim_id);
CREATE INDEX IF NOT EXISTS idx_saga_state_lastevent ON saga_instances (state, last_event_at)
    WHERE state IN ('AWAITING_PENALTY_APPLIED','AWAITING_PENALTY_COUNTED','COMPENSATING_REVOKE');

-- 3. claims.current_saga_id
ALTER TABLE claims ADD COLUMN IF NOT EXISTS current_saga_id UUID;
CREATE INDEX IF NOT EXISTS idx_claims_current_saga ON claims (current_saga_id);

-- 4. outbox_events.saga_id + correlation_id
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS saga_id        UUID;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS correlation_id UUID;
CREATE INDEX IF NOT EXISTS idx_outbox_events_saga ON outbox_events (saga_id);

COMMIT;
