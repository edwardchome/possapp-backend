-- ============================================================================
-- V17: Add branch_id to products table
-- ============================================================================
-- This migration adds:
-- 1. branch_id column to products table for branch-scoped products
-- 2. Index for efficient branch-based queries
-- ============================================================================

-- Create function to add branch_id to products table
CREATE OR REPLACE FUNCTION add_branch_to_products_schema(schema_name text)
RETURNS void AS $$
BEGIN
    -- Check if the schema exists
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = $1) THEN
        
        -- Add branch_id to products table if not exists
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = $1 
            AND table_name = 'products' 
            AND column_name = 'branch_id'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.products ADD COLUMN branch_id VARCHAR(36) REFERENCES %I.branches(id)
            ', $1, $1);
            
            EXECUTE format(
                'CREATE INDEX idx_products_branch ON %I.products(branch_id)',
                $1
            );
            
            RAISE NOTICE 'Added branch_id to products in schema: %', $1;
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
            PERFORM add_branch_to_products_schema(schema_record.schema_name);
        END LOOP;
        
        RAISE NOTICE 'Branch column added to products for all tenant schemas';
    ELSE
        RAISE NOTICE 'No tenant schemas found - branch_id will be added when first tenant registers';
    END IF;
END $$;

-- Clean up the function
DROP FUNCTION IF EXISTS add_branch_to_products_schema(text);

-- Note: For new tenants, the branch_id column
-- is added automatically by TenantService.createTenantTables()
