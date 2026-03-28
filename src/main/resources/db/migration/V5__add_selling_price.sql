-- Migration: Add selling_price column to products table in all tenant schemas
-- This allows products to have a separate selling price from the base price

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
        
        -- Check if products table exists and doesn't have selling_price column
        IF EXISTS (
            SELECT 1 
            FROM information_schema.tables t
            WHERE t.table_schema = target_schema 
              AND t.table_name = 'products'
        ) AND NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns c
            WHERE c.table_schema = target_schema 
              AND c.table_name = 'products' 
              AND c.column_name = 'selling_price'
        ) THEN
            -- Add selling_price column
            EXECUTE format(
                'ALTER TABLE %I.products ADD COLUMN selling_price DECIMAL(10, 2)',
                target_schema);
            
            RAISE NOTICE 'Added selling_price column to products in schema: %', target_schema;
        END IF;
    END LOOP;
END $$;
