-- =============================================================================
-- V3: Seed Data — Super Admin + Platform Configuration Defaults
-- =============================================================================

-- Super Admin Account
-- Email: schoolcanopyadmin@gmail.com
-- Password: Admin@123456 (hash is set by DevSeedInitializer on startup)
INSERT INTO user_account (id, school_id, email, password_hash, name, role, status, failed_login_attempts, created_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    NULL,
    'schoolcanopyadmin@gmail.com',
    '$2a$12$placeholder',
    'Super Admin',
    'SUPER_ADMIN',
    'ACTIVE',
    0,
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- Platform Configuration Defaults
INSERT INTO platform_config (key, value, updated_at) VALUES
    ('session.timeout_minutes', '60', NOW()),
    ('security.max_failed_attempts', '5', NOW()),
    ('security.lockout_duration_minutes', '15', NOW()),
    ('security.password_reset_token_expiry_minutes', '60', NOW()),
    ('password.min_length', '8', NOW()),
    ('password.max_length', '128', NOW()),
    ('password.require_uppercase', 'true', NOW()),
    ('password.require_lowercase', 'true', NOW()),
    ('password.require_number', 'true', NOW()),
    ('password.require_special', 'false', NOW()),
    ('data.max_parents_per_student', '4', NOW()),
    ('data.max_attachments_per_message', '5', NOW()),
    ('data.max_attachment_size_mb', '10', NOW()),
    ('data.message_max_length', '5000', NOW()),
    ('data.invitation_expiry_hours', '48', NOW())
ON CONFLICT (key) DO NOTHING;
