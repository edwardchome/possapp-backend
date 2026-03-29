-- Add missing unit of measurement fields to products table (Repair migration)
-- This ensures all tenant schemas have the required columns

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
        
        -- Check if products table exists in this schema
        IF EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = target_schema AND table_name = 'products'
        ) THEN
            -- Add allow_decimal column if not exists
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'products' 
                  AND column_name = 'allow_decimal'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.products ADD COLUMN allow_decimal BOOLEAN NOT NULL DEFAULT false',
                    target_schema);
            END IF;
            
            -- Add min_quantity column if not exists
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'products' 
                  AND column_name = 'min_quantity'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.products ADD COLUMN min_quantity DECIMAL(10,3) DEFAULT 1.000',
                    target_schema);
            END IF;
            
            -- Add step_quantity column if not exists
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'products' 
                  AND column_name = 'step_quantity'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.products ADD COLUMN step_quantity DECIMAL(10,3) DEFAULT 1.000',
                    target_schema);
            END IF;
            
            -- Add min_stock_level column if not exists
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'products' 
                  AND column_name = 'min_stock_level'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.products ADD COLUMN min_stock_level DECIMAL(12,3) DEFAULT 0.000',
                    target_schema);
            END IF;
            
            -- Ensure stock column is DECIMAL(12,3)
            IF EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'products' 
                  AND column_name = 'stock'
                  AND data_type = 'integer'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.products ALTER COLUMN stock TYPE DECIMAL(12,3) USING stock::DECIMAL(12,3)',
                    target_schema);
            END IF;
        END IF;
    END LOOP;
END $$;
