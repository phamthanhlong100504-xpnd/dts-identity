-- Card 05: simulation mock exams use a distinct capability from learning and theory exams.
-- Keep existing active IDs/metadata, explicit assignments and soft-deleted history.
INSERT INTO permissions (id, name, display_name, resource)
VALUES (gen_random_uuid(), 'simulations:exam', 'Thực hiện và xem kết quả thi thử mô phỏng cá nhân', 'simulations')
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;

-- Ownership, sequential playback and terminal-result checks remain the responsibility of Practice/Media.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'simulations:exam' AND p.deleted_at IS NULL
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')
  AND r.deleted_at IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;
