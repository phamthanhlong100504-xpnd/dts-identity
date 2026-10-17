-- V8 adds chapter permissions on this branch.
-- Existing users remain NULL until they select a license class.
ALTER TABLE users
    ADD COLUMN selected_license_class VARCHAR(10);

ALTER TABLE users
    ADD CONSTRAINT chk_users_selected_license_class
    CHECK (selected_license_class IS NULL OR selected_license_class IN (
        'A1', 'A', 'B1', 'B', 'C1', 'C', 'D1', 'D2', 'D',
        'BE', 'C1E', 'CE', 'D1E', 'D2E', 'DE'
    ));