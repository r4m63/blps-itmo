BEGIN;

-- =====================================================================
-- AUTH-SERVICE DATABASE
-- Users, roles, RBAC privileges. Cross-DB FKs intentionally absent.
-- Script is idempotent: wipes existing objects before recreating.
-- =====================================================================

DROP TABLE IF EXISTS role_privileges CASCADE;
DROP TABLE IF EXISTS privileges CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS outbox_events CASCADE;
DROP TABLE IF EXISTS processed_messages CASCADE;
DROP TYPE  IF EXISTS userrole;

CREATE TYPE userrole AS ENUM ('TENANT', 'LANDLORD', 'ADMIN');

CREATE TABLE users (
    id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role          userrole NOT NULL DEFAULT 'TENANT',
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    penalty_count INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE privileges (
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL
);

CREATE TABLE role_privileges (
    role         userrole NOT NULL,
    privilege_id INT NOT NULL REFERENCES privileges (id) ON DELETE CASCADE,
    PRIMARY KEY (role, privilege_id)
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

-- =====================================================================
-- Privilege catalog
-- =====================================================================
INSERT INTO privileges (code, description) VALUES
    ('CLAIM_CREATE',                  'Создание заявки на штрафные санкции'),
    ('CLAIM_READ_OWN',                'Просмотр своих заявок (арендодатель/арендатор)'),
    ('CLAIM_READ_ANY',                'Просмотр любой заявки (админ)'),
    ('CLAIM_INTAKE_DECISION',         'Первичная проверка заявки админом'),
    ('CLAIM_PROVIDE_ADDITIONAL_INFO', 'Догрузка материалов арендодателем'),
    ('CLAIM_ASSESS',                  'Оценка ущерба админом'),
    ('CLAIM_TENANT_RESPOND',          'Ответ арендатора на претензию'),
    ('CLAIM_SUPPORT_DECISION',        'Финальное решение поддержки'),
    ('STORAGE_UPLOAD',                'Загрузка файлов-доказательств'),
    ('USER_DEACTIVATE',               'Деактивация пользователя');

INSERT INTO role_privileges (role, privilege_id)
SELECT 'LANDLORD'::userrole, id FROM privileges
WHERE code IN ('CLAIM_CREATE','CLAIM_READ_OWN','CLAIM_PROVIDE_ADDITIONAL_INFO','STORAGE_UPLOAD');

INSERT INTO role_privileges (role, privilege_id)
SELECT 'TENANT'::userrole, id FROM privileges
WHERE code IN ('CLAIM_READ_OWN','CLAIM_TENANT_RESPOND','STORAGE_UPLOAD');

INSERT INTO role_privileges (role, privilege_id)
SELECT 'ADMIN'::userrole, id FROM privileges
WHERE code IN ('CLAIM_READ_ANY','CLAIM_INTAKE_DECISION','CLAIM_ASSESS',
               'CLAIM_SUPPORT_DECISION','STORAGE_UPLOAD','USER_DEACTIVATE');

-- =====================================================================
-- Test users (passwords are bcrypt $2y$ — Spring's BCryptPasswordEncoder accepts $2a/$2b/$2y)
--   admin@blps.local    / adminpass    (ADMIN)
--   landlord@blps.local / landpass     (LANDLORD)
--   tenant@blps.local   / tenantpass   (TENANT)
-- =====================================================================
INSERT INTO users (email, password_hash, role) VALUES
    ('admin@blps.local',    '$2y$10$uP5xJ2wujAH1Cv.GfdsZkerWgmwTkZ/q5qBOdpW9/uhGAvnMoz2Z2', 'ADMIN'),
    ('landlord@blps.local', '$2y$10$lh9OHyemo2WM75YfQFqx8e3Op1MsRwxljBqHHBHhHrfxWSLZ2JN6y', 'LANDLORD'),
    ('tenant@blps.local',   '$2y$10$S9onhe68/f4AZnbMinGJSuXlo82xykpoFD4ypBV.p3LOOJB8UjPGC', 'TENANT');

COMMIT;
