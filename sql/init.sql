BEGIN;

CREATE TYPE user_role AS ENUM ('TENANT', 'LANDLORD', 'ADMIN');

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT,
    role user_role NOT NULL DEFAULT 'TENANT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TYPE claim_status AS ENUM (
    'SUBMITTED', -- заявка создана арендодателем
    'INTAKE_REVIEW', -- админ проверяет полноту/формат данных
    'NEED_ADDITIONAL_INFO', -- админ запросил дополнительные материалы
    'UNDER_ASSESSMENT', -- админ оценивает ущерб по правилам
    'AWAITING_TENANT_RESPONSE', -- ждём комментарии/возражения арендатора
    'SUPPORT_REVIEW', -- финальная ручная проверка/поддержка при споре
    'PENALTY_APPLIED', -- штраф подтверждён и применён; заявка закрывается с штрафом
    'CLOSED_NO_PENALTY' -- оснований нет, заявка закрыта без штрафа
);

CREATE TABLE claims (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    landlord_id BIGINT NOT NULL REFERENCES users (id),
    tenant_id BIGINT NOT NULL REFERENCES users (id),
    status claim_status NOT NULL DEFAULT 'SUBMITTED',
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    claimed_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (claimed_amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    assessment_amount NUMERIC(12, 2) CHECK (assessment_amount >= 0),
    assessment_notes TEXT,
    admin_reviewer_id BIGINT REFERENCES users (id), -- единственный админ по заявке
    penalty_amount NUMERIC(12, 2) CHECK (penalty_amount >= 0),
    penalty_currency CHAR(3) DEFAULT 'USD',
    resolution_note TEXT,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    CONSTRAINT chk_claim_parties_distinct CHECK (landlord_id <> tenant_id),
    CONSTRAINT chk_penalty_amount_required CHECK (
        (
            status = 'PENALTY_APPLIED'
            AND penalty_amount IS NOT NULL
        )
        OR (
            status = 'CLOSED_NO_PENALTY'
            AND penalty_amount IS NULL
        )
        OR (
            status NOT IN (
                'PENALTY_APPLIED',
                'CLOSED_NO_PENALTY'
            )
        )
    ),
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

CREATE TYPE comment_type AS ENUM (
    'LANDLORD_STATEMENT', -- первичное заявление/описание от арендодателя
    'ADMIN_NOTE', -- служебная или публичная заметка администратора
    'TENANT_RESPONSE', -- ответ/комментарий/возражение арендатора
    'ADDITIONAL_INFO_REQUEST', -- запрос доп. материалов от админа
    'ADDITIONAL_INFO_REPLY' -- ответ на запрос с доп. информацией
);
-- арендодатель (первичное заявление, ответы на запросы), администратор (запросы доп. данных, служебные заметки)
-- арендатор (комментарии/возражения/согласие).
CREATE TABLE claim_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users (id),
    message_type comment_type NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE claim_status_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    from_status claim_status,
    to_status claim_status NOT NULL,
    actor_id BIGINT REFERENCES users (id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TYPE attachment_purpose AS ENUM (
    'DAMAGE_EVIDENCE', -- доказательства ущерба (фото/видео/акты)
    'ADDITIONAL_MATERIAL', -- материалы, загруженные по запросу или для уточнений
    'SYSTEM' -- служебные вложения
);

CREATE TABLE claim_attachments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    message_id BIGINT REFERENCES claim_messages (id) ON DELETE SET NULL,
    uploaded_by BIGINT NOT NULL REFERENCES users (id),
    purpose attachment_purpose NOT NULL DEFAULT 'DAMAGE_EVIDENCE',
    object_key TEXT NOT NULL UNIQUE,
    file_name TEXT NOT NULL,
    content_type TEXT,
    size_bytes BIGINT CHECK (size_bytes >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
