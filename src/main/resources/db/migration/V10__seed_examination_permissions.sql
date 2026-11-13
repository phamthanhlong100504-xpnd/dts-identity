-- ============================================================
-- V10__seed_examination_permissions.sql
-- Thêm quyền quản lý bài thi (exam), cấu trúc (exam_structure),
-- phiên bản (exam_version), quy chế (exam_rule), tiêu chí (exam_criteria)
-- ============================================================

INSERT INTO permissions (id, name, display_name, resource) VALUES
    -- Exam permissions (theo format chuẩn của Controller)
    ('20000000-0000-0000-0000-000000000001', 'exam:read',                  'Xem danh sách & chi tiết bài thi',   'exam'),
    ('20000000-0000-0000-0000-000000000002', 'exam:create',                'Tạo bài thi mới',                    'exam'),
    ('20000000-0000-0000-0000-000000000003', 'exam:update',                'Cập nhật bài thi',                   'exam'),
    ('20000000-0000-0000-0000-000000000004', 'exam:delete',                'Xóa bài thi',                        'exam'),

    -- Exam Structure permissions
    ('20000000-0000-0000-0000-000000000005', 'exam_structure:read',        'Xem cấu trúc bài thi',               'exam_structure'),
    ('20000000-0000-0000-0000-000000000006', 'exam_structure:create',      'Tạo cấu trúc bài thi',               'exam_structure'),
    ('20000000-0000-0000-0000-000000000007', 'exam_structure:update',      'Cập nhật cấu trúc bài thi',          'exam_structure'),
    ('20000000-0000-0000-0000-000000000008', 'exam_structure:delete',      'Xóa cấu trúc bài thi',               'exam_structure'),

    -- Exam Version permissions
    ('20000000-0000-0000-0000-000000000009', 'exam_version:read',          'Xem phiên bản bài thi',              'exam_version'),
    ('20000000-0000-0000-0000-000000000010', 'exam_version:create',        'Tạo phiên bản bài thi',              'exam_version'),
    ('20000000-0000-0000-0000-000000000011', 'exam_version:update',        'Cập nhật phiên bản bài thi',         'exam_version'),
    ('20000000-0000-0000-0000-000000000012', 'exam_version:delete',        'Xóa phiên bản bài thi',              'exam_version'),
    ('20000000-0000-0000-0000-000000000013', 'exam_version:publish',       'Xuất bản phiên bản bài thi',         'exam_version'),
    ('20000000-0000-0000-0000-000000000014', 'exam_version:archive',       'Lưu trữ phiên bản bài thi',          'exam_version'),
    ('20000000-0000-0000-0000-000000000015', 'exam_version:clone',         'Nhân bản phiên bản bài thi',         'exam_version'),

    -- Exam Rule permissions
    ('20000000-0000-0000-0000-000000000016', 'exam_rule:read',             'Xem quy chế bài thi',                'exam_rule'),
    ('20000000-0000-0000-0000-000000000017', 'exam_rule:create',           'Tạo quy chế bài thi',                'exam_rule'),
    ('20000000-0000-0000-0000-000000000018', 'exam_rule:update',           'Cập nhật quy chế bài thi',           'exam_rule'),
    ('20000000-0000-0000-0000-000000000019', 'exam_rule:delete',           'Xóa quy chế bài thi',                'exam_rule'),

    -- Exam Criteria permissions
    ('20000000-0000-0000-0000-000000000020', 'exam_criteria:read',         'Xem tiêu chí bài thi',               'exam_criteria'),
    ('20000000-0000-0000-0000-000000000021', 'exam_criteria:create',       'Tạo tiêu chí bài thi',               'exam_criteria'),
    ('20000000-0000-0000-0000-000000000022', 'exam_criteria:update',       'Cập nhật tiêu chí bài thi',          'exam_criteria'),
    ('20000000-0000-0000-0000-000000000023', 'exam_criteria:delete',       'Xóa tiêu chí bài thi',               'exam_criteria'),

    -- Exam Session permissions
    ('20000000-0000-0000-0000-000000000024', 'exam_session:read',          'Xem phiên thi của học viên',         'exam_session'),
    ('20000000-0000-0000-0000-000000000025', 'exam_session:create',        'Bắt đầu phiên thi',                  'exam_session'),
    ('20000000-0000-0000-0000-000000000026', 'exam_session:update',        'Làm bài/Nộp bài phiên thi',          'exam_session')
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;

-- 1. Gán tất cả quyền cho ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN'
  AND p.name LIKE 'exam%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 2. Gán quyền tạo, sửa, quản lý bài thi cho ROLE_TEACHER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_TEACHER'
  AND p.name IN (
    'exam:read', 'exam:create', 'exam:update',
    'exam_structure:read', 'exam_structure:create', 'exam_structure:update',
    'exam_version:read', 'exam_version:create', 'exam_version:update', 'exam_version:publish', 'exam_version:archive', 'exam_version:clone',
    'exam_rule:read', 'exam_rule:create', 'exam_rule:update',
    'exam_criteria:read', 'exam_criteria:create', 'exam_criteria:update',
    'exam_session:read'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 3. Gán quyền đọc và làm bài thi cho ROLE_STUDENT
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_STUDENT'
  AND p.name IN (
    'exam:read',
    'exam_session:read', 'exam_session:create', 'exam_session:update'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
