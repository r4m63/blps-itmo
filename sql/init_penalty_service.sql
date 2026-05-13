BEGIN;

-- =====================================================================
-- PENALTY-SERVICE DATABASE
-- Владеет операциями применения штрафа.
-- claim_id, tenant_id, landlord_id, requested_by — INT-ссылки на сущности
-- из других сервисов (claim-service / auth-service). Cross-DB FK
-- отсутствуют намеренно.
-- =====================================================================

CREATE TYPE penaltystatus AS ENUM (
    'REQUESTED',
    'APPLIED',
    'FAILED'
);

CREATE TABLE penalty_operations (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id INT NOT NULL,
    tenant_id INT NOT NULL,
    landlord_id INT NOT NULL,
    requested_by INT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    status penaltystatus NOT NULL DEFAULT 'REQUESTED',
    failure_reason TEXT,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_penalty_parties_distinct CHECK (landlord_id <> tenant_id),
    CONSTRAINT chk_applied_at_present CHECK (
        status <> 'APPLIED'
        OR applied_at IS NOT NULL
    ),
    CONSTRAINT chk_failure_reason_present CHECK (
        status <> 'FAILED'
        OR failure_reason IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_penalty_operations_claim ON penalty_operations (claim_id);

COMMIT;
