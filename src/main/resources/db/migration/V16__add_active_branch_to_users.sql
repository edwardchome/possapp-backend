-- ============================================================================
-- V16: Add active_branch_id to users table
-- ============================================================================
-- This migration adds:
-- 1. active_branch_id column to users table
-- 2. Sets active_branch_id to branch_id if user has a branch assignment
-- ============================================================================

-- Create function to add active_branch_id to users table
CREATE OR REPLACE FUNCTION add_active_branch_to_schema(schema_name text)
RETURNS void AS $$
DECLARE
    main_branch_id varchar(36);
BEGIN
    -- Check if the schema exists
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = $1) THEN
        
        -- Add active_branch_id to users table if not exists
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = $1 
            AND table_name = 'users' 
            AND column_name = 'active_branch_id'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.users ADD COLUMN active_branch_id VARCHAR(36) REFERENCES %I.branches(id)
            ', $1, $1);
            
            EXECUTE format(
                'CREATE INDEX idx_users_active_branch ON %I.users(active_branch_id)',
                $1
            );
            
            RAISE NOTICE 'Added active_branch_id to users in schema: %', $1;
        END IF;
        
        -- Get main branch ID for default assignment
        EXECUTE format('
            SELECT id FROM %I.branches WHERE is_main_branch = true LIMIT 1
        ', $1) INTO main_branch_id;
        
        -- Set active_branch_id to branch_id for users who have a branch assigned
        -- This ensures existing users with branch assignments have an active branch
        IF main_branch_id IS NOT NULL THEN
            EXECUTE format('
                UPDATE %I.users 
                SET active_branch_id = branch_id 
                WHERE branch_id IS NOT NULL AND active_branch_id IS NULL
            ', $1);
            
            RAISE NOTICE 'Updated active_branch_id for existing users in schema: %', $1;
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
            PERFORM add_active_branch_to_schema(schema_record.schema_name);
        END LOOP;
        
        RAISE NOTICE 'Active branch setup completed for all tenant schemas';
    ELSE
        RAISE NOTICE 'No tenant schemas found - active_branch_id will be added when first tenant registers';
    END IF;
END $$;

-- Clean up the function
DROP FUNCTION IF EXISTS add_active_branch_to_schema(text);

-- Note: For new tenants, the active_branch_id column
-- is added automatically by TenantService.createTenantTables()
