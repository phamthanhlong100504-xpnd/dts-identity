-- ============================================================
-- V1__init_identity_schema.sql
-- Identity Service: Quản lý phân quyền, đăng nhập người dùng
-- ============================================================

-- Extension for UUIDv7 generation (optional, falls back to UUIDv4)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- 1. Bảng users - Tài khoản người dùng
-- ============================================================
CREATE TABLE users (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(20)     NOT NULL,
    email           TEXT            NOT NULL,
    password        TEXT            NOT NULL,
    full_name       VARCHAR(100)    NOT NULL,
    birth_of_date   DATE            NOT NULL,
    phone_number    VARCHAR(20)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED', 'BANNED')),
    email_verified_at   TIMESTAMPTZ,
    phone_verified_at   TIMESTAMPTZ,
    failed_login_attempts INT        NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    created_by          UUID        REFERENCES users(id),
    updated_by          UUID        REFERENCES users(id),
    version             BIGINT      NOT NULL DEFAULT 1,
    metadata            JSONB
);

-- Unique partial indexes (ignore soft-deleted records)
CREATE UNIQUE INDEX uq_users_username ON users(username) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_email    ON users(email)    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_phone    ON users(phone_number) WHERE deleted_at IS NULL;

-- Explicit indexes for audit columns
CREATE INDEX idx_users_created_by ON users(created_by);
CREATE INDEX idx_users_updated_by ON users(updated_by);
CREATE INDEX idx_users_status     ON users(status) WHERE deleted_at IS NULL;

-- ============================================================
-- 2. Bảng roles - Vai trò hệ thống
-- ============================================================
CREATE TABLE roles (
    id      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name    VARCHAR(20)     NOT NULL
                CHECK (name IN ('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT', 'ROLE_GUEST'))
);
CREATE UNIQUE INDEX uq_roles_name ON roles(name);

-- ============================================================
-- 3. Bảng user_roles - Gán vai trò cho người dùng (N-N)
-- ============================================================
CREATE TABLE user_roles (
    user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     UUID            NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- ============================================================
-- 4. Bảng permissions - Quyền chi tiết
-- ============================================================
CREATE TABLE permissions (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100)    NOT NULL,
    display_name    VARCHAR(100)    NOT NULL,
    resource        VARCHAR(50)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_permissions_name ON permissions(name) WHERE deleted_at IS NULL;
CREATE INDEX idx_permissions_resource ON permissions(resource);

-- ============================================================
-- 5. Bảng role_permissions - Gán quyền cho vai trò (N-N)
-- ============================================================
CREATE TABLE role_permissions (
    role_id         UUID            NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id   UUID            NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX idx_role_permissions_pid ON role_permissions(permission_id);

-- ============================================================
-- 6. Bảng refresh_tokens - Quản lý phiên đăng nhập
-- ============================================================
CREATE TABLE refresh_tokens (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      TEXT            NOT NULL,
    device_info     JSONB,
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash    ON refresh_tokens(token_hash);

-- ============================================================
-- 7. Bảng verification_codes - Mã xác thực OTP
-- ============================================================
CREATE TABLE verification_codes (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash       TEXT            NOT NULL,
    type            VARCHAR(20)     NOT NULL
                        CHECK (type IN ('REGISTER', 'RESET_PASSWORD', 'CHANGE_EMAIL')),
    attempts        INT             NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ     NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_verification_codes_user_id ON verification_codes(user_id);

-- ============================================================
-- Trigger: Auto-update updated_at
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_permissions_updated_at
    BEFORE UPDATE ON permissions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- SEED DATA: Roles + Permissions mặc định
-- ============================================================

-- Seed Roles
INSERT INTO roles (id, name) VALUES
    (gen_random_uuid(), 'ROLE_ADMIN'),
    (gen_random_uuid(), 'ROLE_TEACHER'),
    (gen_random_uuid(), 'ROLE_STUDENT'),
    (gen_random_uuid(), 'ROLE_GUEST')
ON CONFLICT (name) DO NOTHING;

-- Seed Permissions (resource:action)
INSERT INTO permissions (id, name, display_name, resource) VALUES
    -- User management
    (gen_random_uuid(), 'users:read',    'Xem danh sách người dùng', 'users'),
    (gen_random_uuid(), 'users:create',  'Tạo người dùng',          'users'),
    (gen_random_uuid(), 'users:update',  'Cập nhật người dùng',     'users'),
    (gen_random_uuid(), 'users:delete',  'Xóa người dùng',          'users'),
    -- Role management
    (gen_random_uuid(), 'roles:read',    'Xem danh sách vai trò',   'roles'),
    (gen_random_uuid(), 'roles:assign',  'Gán vai trò',             'roles'),
    (gen_random_uuid(), 'roles:revoke',  'Thu hồi vai trò',         'roles'),
    -- Permission management
    (gen_random_uuid(), 'permissions:read',  'Xem danh sách quyền',  'permissions'),
    (gen_random_uuid(), 'permissions:manage','Quản lý phân quyền',   'permissions'),
    -- Content
    (gen_random_uuid(), 'questions:read',    'Xem câu hỏi',          'questions'),
    (gen_random_uuid(), 'questions:create',  'Tạo câu hỏi',          'questions'),
    (gen_random_uuid(), 'questions:update',  'Cập nhật câu hỏi',     'questions'),
    (gen_random_uuid(), 'questions:delete',  'Xóa câu hỏi',          'questions'),
    (gen_random_uuid(), 'exams:read',        'Xem bài thi',          'exams'),
    (gen_random_uuid(), 'exams:create',      'Tạo bài thi',          'exams'),
    (gen_random_uuid(), 'exams:delete',      'Xóa bài thi',          'exams'),
    (gen_random_uuid(), 'analytics:read',    'Xem báo cáo thống kê', 'analytics')
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;

-- Seed Role-Permission mappings
-- ADMIN: full access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- TEACHER: question + exam management
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_TEACHER'
  AND p.name IN (
    'users:read', 'questions:read', 'questions:create', 'questions:update',
    'exams:read', 'exams:create', 'analytics:read'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STUDENT: read-only + exam access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_STUDENT'
  AND p.name IN ('questions:read', 'exams:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- GUEST: read-only questions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_GUEST'
  AND p.name = 'questions:read'
ON CONFLICT (role_id, permission_id) DO NOTHING;
