-- ============================================================
-- V5__extend_username_length.sql
-- Mở rộng username từ VARCHAR(20) → VARCHAR(50)
-- ============================================================
ALTER TABLE users ALTER COLUMN username TYPE VARCHAR(50);
