-- Permissions consumed by the Progress and Practice API authorization contracts.
-- Add active catalog rows only; keep existing IDs, metadata and historical rows.
INSERT INTO permissions (id, name, display_name, resource)
VALUES
    (gen_random_uuid(), 'progress:read',   'Xem tiến độ học của bản thân',       'progress'),
    (gen_random_uuid(), 'progress:update', 'Cập nhật tiến độ học của bản thân', 'progress'),
    (gen_random_uuid(), 'practice:read',   'Xem nội dung và trạng thái luyện tập', 'practice'),
    (gen_random_uuid(), 'practice:learn',  'Học và ôn tập của bản thân',        'practice'),
    (gen_random_uuid(), 'practice:exam',   'Thực hiện phiên thi thử cá nhân',   'practice'),
    (gen_random_uuid(), 'practice:import', 'Nhập ngân hàng nội dung luyện tập', 'practice')
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;

-- Personal learning rights for the three active built-in authenticated roles.
-- Do not change memberships, existing grants, guest/custom-role policy or users.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.deleted_at IS NULL
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')
  AND r.deleted_at IS NULL
  AND p.name IN (
      'progress:read', 'progress:update',
      'practice:read', 'practice:learn', 'practice:exam'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Shared-bank imports remain an administrative operation in Practice.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'practice:import' AND p.deleted_at IS NULL
WHERE r.name = 'ROLE_ADMIN' AND r.deleted_at IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;
