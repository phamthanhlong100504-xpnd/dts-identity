-- Catalog for simulation learning and the Media/Content Builder permission audit.
-- Keep existing active IDs, metadata, manual grants and soft-deleted history.
INSERT INTO permissions (id, name, display_name, resource)
VALUES
    (gen_random_uuid(), 'simulations:read', 'Xem nội dung và tiến độ luyện mô phỏng', 'simulations'),
    (gen_random_uuid(), 'simulations:learn', 'Thực hiện lượt luyện mô phỏng cá nhân', 'simulations'),
    (gen_random_uuid(), 'media:read', 'Đọc media trong phạm vi được phép', 'media'),
    (gen_random_uuid(), 'media:upload', 'Tải media lên trong phạm vi được phép', 'media'),
    (gen_random_uuid(), 'media:update', 'Cập nhật media trong phạm vi quản trị', 'media'),
    (gen_random_uuid(), 'media:delete', 'Xóa media trong phạm vi quản trị', 'media'),
    (gen_random_uuid(), 'media:manage', 'Quản lý chính sách và media hệ thống', 'media'),
    (gen_random_uuid(), 'simulation-scenarios:read', 'Đọc nội dung gốc và cấu hình chấm mô phỏng', 'simulation-scenarios'),
    (gen_random_uuid(), 'simulation-scenarios:create', 'Nhập nội dung tình huống mô phỏng', 'simulation-scenarios'),
    (gen_random_uuid(), 'simulation-scenarios:publish', 'Công bố ngân hàng mô phỏng', 'simulation-scenarios'),
    (gen_random_uuid(), 'traffic-signs:read', 'Xem biển báo đã công bố', 'traffic-signs'),
    (gen_random_uuid(), 'traffic-signs:create', 'Tạo nội dung biển báo', 'traffic-signs'),
    (gen_random_uuid(), 'memory-tips:read', 'Xem mẹo ghi nhớ đã công bố', 'memory-tips'),
    (gen_random_uuid(), 'memory-tips:create', 'Tạo nội dung mẹo ghi nhớ', 'memory-tips'),
    (gen_random_uuid(), 'learning-programs:read', 'Xem chương trình học trong quản trị', 'learning-programs'),
    (gen_random_uuid(), 'learning-programs:create', 'Tạo chương trình học', 'learning-programs'),
    (gen_random_uuid(), 'learning-programs:update', 'Chỉnh sửa chương trình học', 'learning-programs'),
    (gen_random_uuid(), 'learning-programs:delete', 'Xóa chương trình học', 'learning-programs'),
    (gen_random_uuid(), 'chapters:delete', 'Xóa chương học', 'chapters')
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;

-- A permission never replaces ownership, publication or upload-target checks.
-- Learner media uploads are limited by Media to their permitted personal scope.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.deleted_at IS NULL
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')
  AND r.deleted_at IS NULL
  AND p.name IN (
      'simulations:read', 'simulations:learn', 'media:read', 'media:upload',
      'traffic-signs:read', 'memory-tips:read'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Raw scoring/content, publication and shared-resource mutations are admin scope.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.deleted_at IS NULL
WHERE r.name = 'ROLE_ADMIN' AND r.deleted_at IS NULL
  AND p.name IN (
      'media:update', 'media:delete', 'media:manage',
      'simulation-scenarios:read', 'simulation-scenarios:create', 'simulation-scenarios:publish',
      'traffic-signs:create', 'memory-tips:create',
      'learning-programs:read', 'learning-programs:create',
      'learning-programs:update', 'learning-programs:delete', 'chapters:delete'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
