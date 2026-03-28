-- Migration: Add inventory_transactions table to track stock movements
-- This allows tracking all inventory additions, removals, and adjustments

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
        
        -- Check if inventory_transactions table exists
        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.tables t
            WHERE t.table_schema = target_schema 
              AND t.table_name = 'inventory_transactions'
        ) THEN
            -- Create inventory_transactions table
            EXECUTE format(
                'CREATE TABLE %I.inventory_transactions (
                    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                    product_code VARCHAR(100) NOT NULL,
                    quantity INTEGER NOT NULL,
                    previous_stock INTEGER NOT NULL,
                    new_stock INTEGER NOT NULL,
                    unit_cost DECIMAL(10, 2),
                    total_cost DECIMAL(10, 2),
                    reference_number VARCHAR(255),
                    supplier_name VARCHAR(255),
                    notes TEXT,
                    transaction_type VARCHAR(20) NOT NULL DEFAULT ''STOCK_IN'',
                    created_by VARCHAR(100),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (product_code) REFERENCES %I.products(code)
                )', target_schema, target_schema);
            
            -- Create index for faster lookups
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_inv_trans_product ON %I.inventory_transactions(product_code)',
                target_schema);
            
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_inv_trans_date ON %I.inventory_transactions(created_at)',
                target_schema);
            
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_inv_trans_type ON %I.inventory_transactions(transaction_type)',
                target_schema);
            
            RAISE NOTICE 'Created inventory_transactions table in schema: %', target_schema;
        END IF;
    END LOOP;
END $$;
