-- ============================================================
-- V2__allow_dynamic_roles.sql
-- Cho phép tạo role động: bỏ CHECK constraint, mở rộng length
-- ============================================================

-- 1. Bỏ CHECK constraint trên roles.name để cho phép role động
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_name_check;

-- 2. Mở rộng VARCHAR length từ 20 → 50 (unique index đã có từ V1)
ALTER TABLE roles ALTER COLUMN name TYPE VARCHAR(50);

-- 3. Đảm bảo unique index tồn tại (phòng trường hợp V1 chưa chạy)
CREATE UNIQUE INDEX IF NOT EXISTS uq_roles_name ON roles(name);
