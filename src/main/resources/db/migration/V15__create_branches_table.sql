-- ============================================================================
-- V15: Create branches table for multi-location support
-- ============================================================================
-- This migration adds:
-- 1. branches table to each existing tenant schema
-- 2. branch_id columns to receipts, inventory_transactions, and users tables
-- 3. Creates a default "Main Branch" for each existing tenant
-- 4. Updates existing data to reference the main branch
-- ============================================================================

-- Create function to setup branches for a specific schema
CREATE OR REPLACE FUNCTION setup_branches_for_schema(schema_name text)
RETURNS void AS $$
DECLARE
    main_branch_id uuid;
BEGIN
    -- Check if the schema exists
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = $1) THEN
        
        -- Create branches table if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = $1 AND table_name = 'branches'
        ) THEN
            -- Create branches table
            EXECUTE format('
                CREATE TABLE %I.branches (
                    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                    name VARCHAR(100) NOT NULL,
                    code VARCHAR(20) UNIQUE,
                    address VARCHAR(500),
                    phone_number VARCHAR(50),
                    email VARCHAR(255),
                    is_main_branch BOOLEAN NOT NULL DEFAULT false,
                    is_active BOOLEAN NOT NULL DEFAULT true,
                    can_sell BOOLEAN NOT NULL DEFAULT true,
                    tax_id VARCHAR(100),
                    receipt_header TEXT,
                    receipt_footer TEXT,
                    manager_name VARCHAR(100),
                    operating_hours VARCHAR(500),
                    notes TEXT,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    created_by VARCHAR(36)
                )
            ', $1);
            
            -- Create indexes for branches
            EXECUTE format(
                'CREATE INDEX idx_branches_active ON %I.branches(is_active)',
                $1
            );
            EXECUTE format(
                'CREATE INDEX idx_branches_main ON %I.branches(is_main_branch) WHERE is_main_branch = true',
                $1
            );
            
            RAISE NOTICE 'Created branches table in schema: %', $1;
        END IF;
        
        -- Create default main branch if no branches exist
        EXECUTE format('
            SELECT id FROM %I.branches WHERE is_main_branch = true LIMIT 1
        ', $1) INTO main_branch_id;
        
        IF main_branch_id IS NULL THEN
            EXECUTE format('
                INSERT INTO %I.branches (id, name, code, is_main_branch, is_active, can_sell, created_at, updated_at)
                VALUES (gen_random_uuid(), ''Main Branch'', ''MAIN'', true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
            ', $1) INTO main_branch_id;
            
            RAISE NOTICE 'Created main branch with ID: % in schema: %', main_branch_id, $1;
        END IF;
        
        -- Add branch_id to receipts table if not exists
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = $1 
            AND table_name = 'receipts' 
            AND column_name = 'branch_id'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.receipts ADD COLUMN branch_id VARCHAR(36) REFERENCES %I.branches(id)
            ', $1, $1);
            
            EXECUTE format(
                'CREATE INDEX idx_receipts_branch ON %I.receipts(branch_id)',
                $1
            );
            
            -- Update existing receipts to use main branch
            EXECUTE format(
                'UPDATE %I.receipts SET branch_id = %L WHERE branch_id IS NULL',
                $1, main_branch_id
            );
            
            RAISE NOTICE 'Added branch_id to receipts in schema: %', $1;
        END IF;
        
        -- Add branch_id to inventory_transactions table if not exists
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = $1 
            AND table_name = 'inventory_transactions' 
            AND column_name = 'branch_id'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.inventory_transactions ADD COLUMN branch_id VARCHAR(36) REFERENCES %I.branches(id)
            ', $1, $1);
            
            EXECUTE format(
                'CREATE INDEX idx_inv_trans_branch ON %I.inventory_transactions(branch_id)',
                $1
            );
            
            -- Update existing transactions to use main branch
            EXECUTE format(
                'UPDATE %I.inventory_transactions SET branch_id = %L WHERE branch_id IS NULL',
                $1, main_branch_id
            );
            
            RAISE NOTICE 'Added branch_id to inventory_transactions in schema: %', $1;
        END IF;
        
        -- Add branch_id to users table if not exists
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = $1 
            AND table_name = 'users' 
            AND column_name = 'branch_id'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.users ADD COLUMN branch_id VARCHAR(36) REFERENCES %I.branches(id)
            ', $1, $1);
            
            EXECUTE format(
                'CREATE INDEX idx_users_branch ON %I.users(branch_id)',
                $1
            );
            
            RAISE NOTICE 'Added branch_id to users in schema: %', $1;
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
            PERFORM setup_branches_for_schema(schema_record.schema_name);
        END LOOP;
        
        RAISE NOTICE 'Branches table setup completed for all tenant schemas';
    ELSE
        RAISE NOTICE 'No tenant schemas found - branches table will be created when first tenant registers';
    END IF;
END $$;

-- Clean up the function
DROP FUNCTION IF EXISTS setup_branches_for_schema(text);

-- Note: For new tenants, the branches table and default main branch
-- are created automatically by TenantService.createTenantTables()
