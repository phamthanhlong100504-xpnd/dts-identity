-- ============================================================
-- V4__add_audit_fields_to_master_data.sql
-- Bổ sung audit fields cho bảng danh mục: roles, permissions
-- + assigned_at cho role_permissions
-- ============================================================

-- ============================================================
-- 1. Bảng roles: thêm audit + metadata + soft-delete
-- ============================================================
ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS metadata    JSONB,
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS deleted_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by  UUID,
    ADD COLUMN IF NOT EXISTS updated_by  UUID;

-- Index cho soft-delete query
CREATE INDEX IF NOT EXISTS idx_roles_deleted_at ON roles(deleted_at);

-- Trigger auto-update updated_at
DROP TRIGGER IF EXISTS trg_roles_updated_at ON roles;
CREATE TRIGGER trg_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 2. Bảng permissions: thêm metadata + created_by + updated_by
--    (created_at, updated_at, deleted_at đã có từ V1)
-- ============================================================
ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS metadata    JSONB,
    ADD COLUMN IF NOT EXISTS created_by  UUID,
    ADD COLUMN IF NOT EXISTS updated_by  UUID;

-- ============================================================
-- 3. Bảng role_permissions: thêm assigned_at
-- ============================================================
ALTER TABLE role_permissions
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_role_permissions_assigned ON role_permissions(assigned_at);
