-- Fix inventory_transactions table columns for BigDecimal support
-- Changes quantity, previous_stock, new_stock from INTEGER to DECIMAL

DO $$
DECLARE
    schema_rec RECORD;
    target_schema TEXT;
BEGIN
    FOR schema_rec IN 
        SELECT s.schema_name AS sname
        FROM information_schema.schemata s
        WHERE s.schema_name NOT IN ('pg_catalog', 'information_schema', 'public')
          AND s.schema_name NOT LIKE 'pg_%'
    LOOP
        target_schema := schema_rec.sname;
        
        -- Check if inventory_transactions table exists in this schema
        IF EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = target_schema AND table_name = 'inventory_transactions'
        ) THEN
            -- Alter quantity column to DECIMAL(15,4)
            IF EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'inventory_transactions' 
                  AND column_name = 'quantity'
                  AND data_type = 'integer'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.inventory_transactions ALTER COLUMN quantity TYPE DECIMAL(15,4) USING quantity::DECIMAL(15,4)',
                    target_schema);
            END IF;
            
            -- Add quantity column if not exists
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'inventory_transactions' 
                  AND column_name = 'quantity'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.inventory_transactions ADD COLUMN quantity DECIMAL(15,4) NOT NULL DEFAULT 0',
                    target_schema);
            END IF;
            
            -- Alter previous_stock column to DECIMAL(15,4)
            IF EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'inventory_transactions' 
                  AND column_name = 'previous_stock'
                  AND data_type = 'integer'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.inventory_transactions ALTER COLUMN previous_stock TYPE DECIMAL(15,4) USING previous_stock::DECIMAL(15,4)',
                    target_schema);
            END IF;
            
            -- Add previous_stock column if not exists
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'inventory_transactions' 
                  AND column_name = 'previous_stock'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.inventory_transactions ADD COLUMN previous_stock DECIMAL(15,4) NOT NULL DEFAULT 0',
                    target_schema);
            END IF;
            
            -- Alter new_stock column to DECIMAL(15,4)
            IF EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'inventory_transactions' 
                  AND column_name = 'new_stock'
                  AND data_type = 'integer'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.inventory_transactions ALTER COLUMN new_stock TYPE DECIMAL(15,4) USING new_stock::DECIMAL(15,4)',
                    target_schema);
            END IF;
            
            -- Add new_stock column if not exists
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'inventory_transactions' 
                  AND column_name = 'new_stock'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.inventory_transactions ADD COLUMN new_stock DECIMAL(15,4) NOT NULL DEFAULT 0',
                    target_schema);
            END IF;
        END IF;
    END LOOP;
END $$;
