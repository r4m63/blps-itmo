-- =====================================================================
-- Тестовые пользователи для auth / RBAC базы (DB 2).
-- Имя файла оставлено по запросу, но запускать его нужно там, где
-- находится таблица users.
-- Пароль у всех: "password"
-- BCrypt hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- Аутентификация: HTTP Basic, логин = email.
-- =====================================================================
BEGIN;

INSERT INTO
    users (
        email,
        password_hash,
        role,
        enabled,
        created_at,
        updated_at
    )
VALUES (
        'landlord1@example.com',
        '$2a$10$Gbnr193OikjLUl8r9NmhruksWyubX3dZEqI3bGnR6oETpdHHMNMHC',
        'LANDLORD',
        TRUE,
        now(),
        now()
    ),
    (
        'landlord2@example.com',
        '$2a$10$Gbnr193OikjLUl8r9NmhruksWyubX3dZEqI3bGnR6oETpdHHMNMHC',
        'LANDLORD',
        TRUE,
        now(),
        now()
    ),
    (
        'landlord3@example.com',
        '$2a$10$Gbnr193OikjLUl8r9NmhruksWyubX3dZEqI3bGnR6oETpdHHMNMHC',
        'LANDLORD',
        TRUE,
        now(),
        now()
    ),
    (
        'tenant1@example.com',
        '$2a$10$Gbnr193OikjLUl8r9NmhruksWyubX3dZEqI3bGnR6oETpdHHMNMHC',
        'TENANT',
        TRUE,
        now(),
        now()
    ),
    (
        'tenant2@example.com',
        '$2a$10$Gbnr193OikjLUl8r9NmhruksWyubX3dZEqI3bGnR6oETpdHHMNMHC',
        'TENANT',
        TRUE,
        now(),
        now()
    ),
    (
        'tenant3@example.com',
        '$2a$10$Gbnr193OikjLUl8r9NmhruksWyubX3dZEqI3bGnR6oETpdHHMNMHC',
        'TENANT',
        TRUE,
        now(),
        now()
    ),
    (
        'admin1@example.com',
        '$2a$10$Gbnr193OikjLUl8r9NmhruksWyubX3dZEqI3bGnR6oETpdHHMNMHC',
        'ADMIN',
        TRUE,
        now(),
        now()
    ),
    (
        'admin2@example.com',
        '$2a$10$Gbnr193OikjLUl8r9NmhruksWyubX3dZEqI3bGnR6oETpdHHMNMHC',
        'ADMIN',
        TRUE,
        now(),
        now()
    );

COMMIT;
