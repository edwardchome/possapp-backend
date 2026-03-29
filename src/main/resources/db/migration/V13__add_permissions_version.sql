-- Add permissions_version column to users table for all existing tenant schemas
-- This is used to track permission changes and force logout when permissions are modified

-- Create a function to add the column to a specific schema (only if table exists)
CREATE OR REPLACE FUNCTION add_permissions_version_to_schema(schema_name text)
RETURNS void AS $$
BEGIN
    -- Check if the schema exists
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = $1) THEN
        -- Check if the users table exists in this schema
        IF EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = $1 AND table_name = 'users'
        ) THEN
            -- Check if the column doesn't already exist
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = $1 
                AND table_name = 'users' 
                AND column_name = 'permissions_version'
            ) THEN
                -- Add the column
                EXECUTE format('ALTER TABLE %I.users ADD COLUMN permissions_version BIGINT NOT NULL DEFAULT 1', $1);
                -- Add comment
                EXECUTE format('COMMENT ON COLUMN %I.users.permissions_version IS ''Version number that increments when user role or permissions change. Used to invalidate JWT tokens.''', $1);
            END IF;
        END IF;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Apply to all tenant schemas (exclude public, information_schema, pg_*)
-- Skip if no tenant schemas exist yet (fresh database)
DO $$
DECLARE
    schema_record RECORD;
    has_tenant_schemas BOOLEAN := false;
BEGIN
    -- Check if any tenant schemas exist
    SELECT EXISTS (
        SELECT 1 FROM information_schema.schemata 
        WHERE schema_name NOT IN ('public', 'information_schema')
        AND schema_name NOT LIKE 'pg_%'
    ) INTO has_tenant_schemas;
    
    -- Only process if tenant schemas exist
    IF has_tenant_schemas THEN
        FOR schema_record IN 
            SELECT schema_name 
            FROM information_schema.schemata 
            WHERE schema_name NOT IN ('public', 'information_schema')
            AND schema_name NOT LIKE 'pg_%'
        LOOP
            PERFORM add_permissions_version_to_schema(schema_record.schema_name);
        END LOOP;
    END IF;
END $$;

-- Clean up the function
DROP FUNCTION IF EXISTS add_permissions_version_to_schema(text);

-- Note: public.users table doesn't exist in this architecture
-- Users table is created per-tenant by TenantService.createTenantTables()
-- The permissions_version column is added there for new tenants
-- This migration handles existing tenant schemas only
