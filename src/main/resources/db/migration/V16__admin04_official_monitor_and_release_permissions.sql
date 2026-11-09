-- ADM04 G3: separate monitoring and manual result release from generic exam edit.
-- Additive only: no active student/teacher rights and no existing grant removed.
INSERT INTO permissions (id, name, display_name, resource) VALUES
  (gen_random_uuid(), 'exam_session:read', 'Theo doi phien thi chinh thuc (quan tri)', 'exam_session'),
  (gen_random_uuid(), 'official-exams:release-result', 'Cong bo ket qua thi chinh thuc thu cong', 'official-exams')
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.deleted_at IS NULL
WHERE r.name='ROLE_ADMIN' AND r.deleted_at IS NULL
  AND p.name IN ('exam_session:read', 'official-exams:release-result')
ON CONFLICT (role_id, permission_id) DO NOTHING;
