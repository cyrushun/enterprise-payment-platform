INSERT INTO customers
(id, first_name, last_name, email, phone_number, status, created_at, updated_at)
VALUES(gen_random_uuid(), 'operations', '', 'operations@test.local', '012-xxxxxxx', 'ACTIVE', '2026-11-06 15:19:44.735', '2026-11-06 15:19:44.735');
INSERT INTO customers
(id, first_name, last_name, email, phone_number, status, created_at, updated_at)
VALUES(gen_random_uuid(), 'admin', '', 'admin@test.local', '012-xxxxxxx', 'ACTIVE', '2026-11-06 15:19:44.735', '2026-11-06 15:19:44.735');

INSERT INTO users (
	id,
    customer_id,
    email,
    password_hash,
    status,
    created_at,
    updated_at    
)
VALUES (
	gen_random_uuid(),
    (SELECT id FROM customers WHERE email = 'operations@test.local'),
    'operations@test.local',
    '$2a$10$95f9T5avR1AIqd4fabPTXOW.8jh1/sVwmL8iyBNFx96c4TjOdNMne',
    'ACTIVE',
    NOW(),
    NOW()    
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (
	id,
    customer_id,
    email,
    password_hash,
    status,
    created_at,
    updated_at    
)
VALUES (
	gen_random_uuid(),
    (SELECT id FROM customers WHERE email = 'admin@test.local'),
    'admin@test.local',
    '$2a$10$rP4rSaReEjDJOiA6uhN01uUJYMkf7LpC410gUmmnkMyJz1Q//4Shy',
    'ACTIVE',
    NOW(),
    NOW()      
)
ON CONFLICT (email) DO NOTHING;

-- Assign OPERATIONS role.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'OPERATIONS'
WHERE u.email = 'operations@test.local'
ON CONFLICT DO NOTHING;

-- Assign ADMIN role.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = 'admin@test.local'
ON CONFLICT DO NOTHING;