-- Official examination configuration, publication and manual result release.
INSERT INTO permissions (id, name, display_name, resource)
VALUES (gen_random_uuid(), 'exams:update', 'Cap nhat va cong bo ky thi', 'exams')
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'exams:update' AND p.deleted_at IS NULL
WHERE r.name = 'ROLE_ADMIN' AND r.deleted_at IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;
