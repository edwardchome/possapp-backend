-- Migration: Add user permission columns for granular access control
-- This allows tenant admins to control which users can manage products and inventory

DO $$
DECLARE
    schema_rec RECORD;
    target_schema TEXT;
BEGIN
    -- Iterate through all schemas except system schemas
    FOR schema_rec IN 
        SELECT s.schema_name AS sname
        FROM information_schema.schemata s
        WHERE s.schema_name NOT IN ('pg_catalog', 'information_schema', 'public')
          AND s.schema_name NOT LIKE 'pg_%'
    LOOP
        target_schema := schema_rec.sname;
        
        -- Check if users table exists and add can_manage_products column
        IF EXISTS (
            SELECT 1 
            FROM information_schema.tables t
            WHERE t.table_schema = target_schema 
              AND t.table_name = 'users'
        ) AND NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns c
            WHERE c.table_schema = target_schema 
              AND c.table_name = 'users' 
              AND c.column_name = 'can_manage_products'
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I.users ADD COLUMN can_manage_products BOOLEAN NOT NULL DEFAULT false',
                target_schema);
            
            RAISE NOTICE 'Added can_manage_products column to users table in schema: %', target_schema;
        END IF;
        
        -- Check if users table exists and add can_manage_inventory column
        IF EXISTS (
            SELECT 1 
            FROM information_schema.tables t
            WHERE t.table_schema = target_schema 
              AND t.table_name = 'users'
        ) AND NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns c
            WHERE c.table_schema = target_schema 
              AND c.table_name = 'users' 
              AND c.column_name = 'can_manage_inventory'
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I.users ADD COLUMN can_manage_inventory BOOLEAN NOT NULL DEFAULT false',
                target_schema);
            
            RAISE NOTICE 'Added can_manage_inventory column to users table in schema: %', target_schema;
        END IF;
        
        -- Grant permissions to existing ADMIN and MANAGER users
        EXECUTE format(
            'UPDATE %I.users SET can_manage_products = true, can_manage_inventory = true WHERE role IN (''ADMIN'', ''MANAGER'')',
            target_schema);
        
    END LOOP;
END $$;
