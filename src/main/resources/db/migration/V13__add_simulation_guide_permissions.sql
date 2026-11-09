-- Card 04: guide editing/publication is distinct from simulation scoring and bank administration.
-- Preserve active IDs/metadata/manual assignments and soft-deleted history.
INSERT INTO permissions (id, name, display_name, resource)
VALUES
    (gen_random_uuid(), 'simulation-guides:read', 'Đọc bản nháp và lịch sử hướng dẫn mô phỏng', 'simulation-guides'),
    (gen_random_uuid(), 'simulation-guides:update', 'Lưu bản nháp hướng dẫn mô phỏng', 'simulation-guides'),
    (gen_random_uuid(), 'simulation-guides:publish', 'Công bố hướng dẫn mô phỏng', 'simulation-guides')
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;

-- Learners continue to read published guides with the existing simulations:read right.
-- Content Builder also checks ROLE_ADMIN, so a permission alone does not confer administration.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.deleted_at IS NULL
WHERE r.name = 'ROLE_ADMIN' AND r.deleted_at IS NULL
  AND p.name IN ('simulation-guides:read', 'simulation-guides:update', 'simulation-guides:publish')
ON CONFLICT (role_id, permission_id) DO NOTHING;
