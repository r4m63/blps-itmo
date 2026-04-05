BEGIN;

-- =====================================================================
-- Reset schema for lab environment: drop old tables and types if exist
-- =====================================================================
DROP TABLE IF EXISTS claim_attachments CASCADE;
DROP TABLE IF EXISTS claim_status_history CASCADE;
DROP TABLE IF EXISTS claim_messages CASCADE;
DROP TABLE IF EXISTS claims CASCADE;
DROP TABLE IF EXISTS role_privileges CASCADE;
DROP TABLE IF EXISTS privileges CASCADE;
DROP TABLE IF EXISTS users CASCADE;

DROP TYPE IF EXISTS attachment_purpose CASCADE;
DROP TYPE IF EXISTS attachmentpurpose CASCADE;
DROP TYPE IF EXISTS comment_type CASCADE;
DROP TYPE IF EXISTS commenttype CASCADE;
DROP TYPE IF EXISTS claim_status CASCADE;
DROP TYPE IF EXISTS claimstatus CASCADE;
DROP TYPE IF EXISTS user_role CASCADE;
DROP TYPE IF EXISTS userrole CASCADE;

-- =====================================================================
-- USERS & ACCESS CONTROL (lab 2: RBAC + privileges)
-- =====================================================================
-- Три роли участников бизнес-процесса сервиса заявок на штрафные санкции:
--   TENANT   — арендатор (ответчик по заявке)
--   LANDLORD — арендодатель (инициатор заявки о штрафе)
--   ADMIN    — администратор сервиса (модератор/поддержка Airbnb)
CREATE TYPE userrole AS ENUM ('TENANT', 'LANDLORD', 'ADMIN');

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role userrole NOT NULL DEFAULT 'TENANT',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Справочник привилегий (атомарные права на операции бизнес-логики).
-- Код привилегии используется как GrantedAuthority в Spring Security
-- и проверяется в @PreAuthorize("hasAuthority('...')").
CREATE TABLE privileges (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL
);

-- Маппинг роль -> множество привилегий (many-to-many на уровне роли).
-- Позволяет менять матрицу доступа без перекомпиляции приложения.
CREATE TABLE role_privileges (
    role userrole NOT NULL,
    privilege_id BIGINT NOT NULL REFERENCES privileges (id) ON DELETE CASCADE,
    PRIMARY KEY (role, privilege_id)
);

-- ---------------------------------------------------------------------
-- Набор привилегий (специфицированная политика доступа)
-- ---------------------------------------------------------------------
INSERT INTO
    privileges (code, description)
VALUES (
        'CLAIM_CREATE',
        'Создание заявки на штрафные санкции'
    ),
    (
        'CLAIM_READ_OWN',
        'Просмотр заявок, в которых пользователь является стороной (арендодатель/арендатор)'
    ),
    (
        'CLAIM_READ_ANY',
        'Просмотр любой заявки в системе (административный доступ)'
    ),
    (
        'CLAIM_INTAKE_DECISION',
        'Первичная проверка заявки администратором: полнота данных, запрос доп. материалов'
    ),
    (
        'CLAIM_PROVIDE_ADDITIONAL_INFO',
        'Предоставление арендодателем дополнительных материалов по запросу администратора'
    ),
    (
        'CLAIM_ASSESS',
        'Оценка ущерба и определение оснований для штрафа администратором'
    ),
    (
        'CLAIM_TENANT_RESPOND',
        'Ответ/возражение арендатора на претензию в рамках заявки'
    ),
    (
        'CLAIM_SUPPORT_DECISION',
        'Финальное решение поддержки: применить штраф либо закрыть без штрафа'
    ),
    (
        'STORAGE_UPLOAD',
        'Инициация и подтверждение загрузки файлов-доказательств в объектное хранилище'
    );

-- ---------------------------------------------------------------------
-- Матрица ролей и привилегий
-- ---------------------------------------------------------------------
-- LANDLORD: создаёт заявки, видит свои, дополняет по запросу, загружает файлы
INSERT INTO
    role_privileges (role, privilege_id)
SELECT 'LANDLORD'::userrole, id
FROM privileges
WHERE
    code IN (
        'CLAIM_CREATE',
        'CLAIM_READ_OWN',
        'CLAIM_PROVIDE_ADDITIONAL_INFO',
        'STORAGE_UPLOAD'
    );

-- TENANT: видит свои заявки, отвечает на претензии, загружает файлы-возражения
INSERT INTO
    role_privileges (role, privilege_id)
SELECT 'TENANT'::userrole, id
FROM privileges
WHERE
    code IN (
        'CLAIM_READ_OWN',
        'CLAIM_TENANT_RESPOND',
        'STORAGE_UPLOAD'
    );

-- ADMIN: полный модерационный контроль над заявками
INSERT INTO
    role_privileges (role, privilege_id)
SELECT 'ADMIN'::userrole, id
FROM privileges
WHERE
    code IN (
        'CLAIM_READ_ANY',
        'CLAIM_INTAKE_DECISION',
        'CLAIM_ASSESS',
        'CLAIM_SUPPORT_DECISION',
        'STORAGE_UPLOAD'
    );

-- =====================================================================
-- CLAIMS (бизнес-процесс)
-- =====================================================================
CREATE TYPE claimstatus AS ENUM (
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
    status claimstatus NOT NULL DEFAULT 'SUBMITTED',
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

CREATE TYPE commenttype AS ENUM (
    'LANDLORD_STATEMENT', -- первичное заявление/описание от арендодателя
    'ADMIN_NOTE', -- служебная или публичная заметка администратора
    'TENANT_RESPONSE', -- ответ/комментарий/возражение арендатора
    'ADDITIONAL_INFO_REQUEST', -- запрос доп. материалов от админа
    'ADDITIONAL_INFO_REPLY' -- ответ на запрос с доп. информацией
);

CREATE TABLE claim_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users (id),
    message_type commenttype NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE claim_status_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    from_status claimstatus,
    to_status claimstatus NOT NULL,
    actor_id BIGINT REFERENCES users (id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TYPE attachmentpurpose AS ENUM (
    'DAMAGE_EVIDENCE', -- доказательства ущерба (фото/видео/акты)
    'ADDITIONAL_MATERIAL', -- материалы, загруженные по запросу или для уточнений
    'SYSTEM' -- служебные вложения
);

CREATE TABLE claim_attachments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id BIGINT REFERENCES claims (id) ON DELETE CASCADE,
    message_id BIGINT REFERENCES claim_messages (id) ON DELETE SET NULL,
    uploaded_by BIGINT NOT NULL REFERENCES users (id),
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
