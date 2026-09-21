-- V10: Seed base roles and permissions
-- These are seeded unconditionally so the schema is consistent across environments.
-- The admin user itself is created only when SEED_ENABLED=true via the application runner.

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'user:read',        'Read user information'),
    (gen_random_uuid(), 'user:create',      'Create new users'),
    (gen_random_uuid(), 'user:update',      'Update user information'),
    (gen_random_uuid(), 'user:delete',      'Delete users'),
    (gen_random_uuid(), 'role:read',        'Read role information'),
    (gen_random_uuid(), 'role:create',      'Create new roles'),
    (gen_random_uuid(), 'role:update',      'Update role information'),
    (gen_random_uuid(), 'role:delete',      'Delete roles'),
    (gen_random_uuid(), 'permission:read',  'Read permission information')
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN',   'Full administrative access'),
    ('00000000-0000-0000-0000-000000000002', 'MANAGER', 'Managerial access'),
    ('00000000-0000-0000-0000-000000000003', 'USER',    'Standard user access')
ON CONFLICT (name) DO NOTHING;

-- Grant all identity permissions to ADMIN role
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', id FROM permissions
ON CONFLICT DO NOTHING;

-- Grant read permissions to MANAGER role
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id
FROM permissions
WHERE code IN ('user:read', 'role:read', 'permission:read')
ON CONFLICT DO NOTHING;

-- Grant basic read to USER role
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003', id
FROM permissions
WHERE code IN ('user:read')
ON CONFLICT DO NOTHING;
