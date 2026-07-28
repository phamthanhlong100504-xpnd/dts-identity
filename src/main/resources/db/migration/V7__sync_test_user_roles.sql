-- Flyway Migration V7: Sync default test user roles for VPS & Local DB

-- 1. Assign ROLE_ADMIN and ROLE_STUDENT to testuser2
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'testuser2'
  AND r.name IN ('ROLE_ADMIN', 'ROLE_STUDENT')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- 2. Assign ROLE_STUDENT to student01
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'student01'
  AND r.name = 'ROLE_STUDENT'
ON CONFLICT (user_id, role_id) DO NOTHING;
