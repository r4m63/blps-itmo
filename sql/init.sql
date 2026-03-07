-- Initial schema for penalty claim service
-- Tables match JPA entities Claim and Attachment

CREATE TABLE IF NOT EXISTS claim (
    id                  BIGSERIAL PRIMARY KEY,
    initiator_id        TEXT        NOT NULL, -- арендодатель
    respondent_id       TEXT        NOT NULL, -- арендатор
    amount              NUMERIC(19,2) NOT NULL,
    currency            VARCHAR(8)  NOT NULL,
    reason              TEXT        NOT NULL,
    status              VARCHAR(48) NOT NULL,
    enough_data         BOOLEAN,
    grounds_for_penalty BOOLEAN,
    approve_penalty     BOOLEAN,
    penalty_applied     BOOLEAN,
    respondent_timeout  BOOLEAN,
    respondent_comment  TEXT,
    support_comment     TEXT,
    response_due_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS attachment (
    id          BIGSERIAL PRIMARY KEY,
    claim_id    BIGINT      NOT NULL REFERENCES claim(id) ON DELETE CASCADE,
    uploader_id TEXT        NOT NULL,
    type        VARCHAR(32) NOT NULL,
    url         TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_claim_status ON claim(status);
CREATE INDEX IF NOT EXISTS idx_claim_response_due ON claim(response_due_at);
CREATE INDEX IF NOT EXISTS idx_attachment_claim ON attachment(claim_id);
