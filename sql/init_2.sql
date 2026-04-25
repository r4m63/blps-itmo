BEGIN;

-- =====================================================================
-- AUTH / RBAC DATABASE (DB 2)
-- Здесь хранятся пользователи, роли и привилегии.
-- =====================================================================

CREATE TYPE userrole AS ENUM ('TENANT', 'LANDLORD', 'ADMIN');

CREATE TABLE users (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role userrole NOT NULL DEFAULT 'TENANT',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    penalty_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE privileges (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL
);

CREATE TABLE role_privileges (
    role userrole NOT NULL,
    privilege_id INT NOT NULL REFERENCES privileges (id) ON DELETE CASCADE,
    PRIMARY KEY (role, privilege_id)
);

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
    ),
    (
        'USER_DEACTIVATE',
        'Деактивация пользователя с закрытием всех его открытых заявок'
    );

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
        'STORAGE_UPLOAD',
        'USER_DEACTIVATE'
    );

COMMIT;
