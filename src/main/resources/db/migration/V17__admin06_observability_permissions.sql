-- DTS ADM06: additive, narrowly scoped read access; no privilege escalation to learners.
-- Existing reports:read and admin-audit:read remain unchanged.
-- integrations:retry is catalogued but intentionally NOT assigned to any role.
INSERT INTO permissions (id, name, display_name, resource) VALUES
    (gen_random_uuid(), 'dashboard:read', 'Xem Dashboard quan tri DTS', 'dashboard'),
    (gen_random_uuid(), 'audit:read', 'Doc nhat ky quan tri lien service', 'audit'),
    (gen_random_uuid(), 'integrations:read', 'Doc trang thai tich hop va doi soat', 'integrations'),
    (gen_random_uuid(), 'integrations:retry', 'Thu lai su kien da xac minh (chua kich hoat)', 'integrations'),
    (gen_random_uuid(), 'system-status:read', 'Doc trang thai van hanh DTS', 'system-status')
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;

-- Only explicit READ rights are provisioned to the existing administrator role.
-- Do not grant integrations:retry or any new rights to teacher/student/guest.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.deleted_at IS NULL
WHERE r.name = 'ROLE_ADMIN' AND r.deleted_at IS NULL
  AND p.name IN ('dashboard:read', 'audit:read', 'integrations:read', 'system-status:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;
