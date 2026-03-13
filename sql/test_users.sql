BEGIN;

INSERT INTO
    users (email, password_hash, role)
VALUES (
        'landlord1@example.com',
        'pwd-landlord1',
        'LANDLORD'
    ),
    (
        'landlord2@example.com',
        'pwd-landlord2',
        'LANDLORD'
    ),
    (
        'landlord3@example.com',
        'pwd-landlord3',
        'LANDLORD'
    ),
    (
        'tenant1@example.com',
        'pwd-tenant1',
        'TENANT'
    ),
    (
        'tenant2@example.com',
        'pwd-tenant2',
        'TENANT'
    ),
    (
        'tenant3@example.com',
        'pwd-tenant3',
        'TENANT'
    ),
    (
        'admin1@example.com',
        'pwd-admin1',
        'ADMIN'
    ),
    (
        'admin2@example.com',
        'pwd-admin2',
        'ADMIN'
    );

COMMIT;
