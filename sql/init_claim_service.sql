BEGIN;

-- =====================================================================
-- CLAIM-SERVICE DATABASE
-- Владеет жизненным циклом заявок: claims, сообщения, история статусов,
-- вложения.
-- landlord_id, tenant_id, admin_reviewer_id, uploaded_by — это INT-ссылки
-- на users в auth-service. Cross-DB FK отсутствуют намеренно.
-- penalty_amount / penalty_currency не хранятся: операция штрафа живёт
-- в penalty-service. Статус PENALTY_APPLIED здесь означает только
-- факт применения; детали запрашиваются у penalty-service.
-- =====================================================================

CREATE TYPE claimstatus AS ENUM (
    'SUBMITTED',
    'INTAKE_REVIEW',
    'NEED_ADDITIONAL_INFO',
    'UNDER_ASSESSMENT',
    'AWAITING_TENANT_RESPONSE',
    'SUPPORT_REVIEW',
    'PENALTY_APPLIED',
    'CLOSED_NO_PENALTY'
);

CREATE TABLE claims (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    landlord_id INT NOT NULL,
    tenant_id INT NOT NULL,
    status claimstatus NOT NULL DEFAULT 'SUBMITTED',
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    claimed_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (claimed_amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    assessment_amount NUMERIC(12, 2) CHECK (assessment_amount >= 0),
    assessment_notes TEXT,
    admin_reviewer_id INT,
    resolution_note TEXT,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    CONSTRAINT chk_claim_parties_distinct CHECK (landlord_id <> tenant_id),
    CONSTRAINT chk_terminal_requires_admin CHECK (
        status NOT IN (
            'PENALTY_APPLIED',
            'CLOSED_NO_PENALTY'
        )
        OR admin_reviewer_id IS NOT NULL
    ),
    CONSTRAINT chk_decision_presence CHECK (
        status NOT IN (
            'PENALTY_APPLIED',
            'CLOSED_NO_PENALTY'
        )
        OR (
            decided_at IS NOT NULL
            AND closed_at IS NOT NULL
        )
    )
);

CREATE TYPE commenttype AS ENUM (
    'LANDLORD_STATEMENT',
    'ADMIN_NOTE',
    'TENANT_RESPONSE',
    'ADDITIONAL_INFO_REQUEST',
    'ADDITIONAL_INFO_REPLY'
);

CREATE TABLE claim_messages (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id INT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    user_id INT NOT NULL,
    message_type commenttype NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE claim_status_history (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id INT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    from_status claimstatus,
    to_status claimstatus NOT NULL,
    actor_id INT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TYPE attachmentpurpose AS ENUM (
    'DAMAGE_EVIDENCE',
    'ADDITIONAL_MATERIAL',
    'SYSTEM'
);

CREATE TABLE claim_attachments (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id INT REFERENCES claims (id) ON DELETE CASCADE,
    message_id INT REFERENCES claim_messages (id) ON DELETE SET NULL,
    uploaded_by INT NOT NULL,
    purpose attachmentpurpose NOT NULL DEFAULT 'DAMAGE_EVIDENCE',
    object_key TEXT NOT NULL UNIQUE,
    file_name TEXT NOT NULL,
    content_type TEXT,
    size_bytes BIGINT CHECK (size_bytes >= 0),
    uploaded BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
