-- Quyền cho API quản lý chương của Content Builder.
INSERT INTO permissions (id, name, display_name, resource)
VALUES
    (gen_random_uuid(), 'chapters:read',   'Xem chương học',  'chapters'),
    (gen_random_uuid(), 'chapters:create', 'Tạo chương học', 'chapters'),
    (gen_random_uuid(), 'chapters:update', 'Sửa chương học', 'chapters')
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;

-- ROLE_ADMIN đã có từ migration trước nhưng cần nhận các quyền mới.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN'
  AND p.name IN (
      'chapters:read',
      'chapters:create',
      'chapters:update'
  )
  AND p.deleted_at IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;