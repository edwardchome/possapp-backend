-- Add permissions_version column to users table
-- This is used to track permission changes and force logout when permissions are modified

ALTER TABLE users ADD COLUMN IF NOT EXISTS permissions_version BIGINT NOT NULL DEFAULT 1;

-- Add comment to explain the column purpose
COMMENT ON COLUMN users.permissions_version IS 'Version number that increments when user role or permissions change. Used to invalidate JWT tokens.';
