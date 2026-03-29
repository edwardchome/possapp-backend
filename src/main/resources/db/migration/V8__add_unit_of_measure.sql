-- Add unit of measurement fields to products table
-- This migration handles schema-per-tenant setup

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
            -- Add unit_of_measure column if not exists
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'products' 
                  AND column_name = 'unit_of_measure'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.products ADD COLUMN unit_of_measure VARCHAR(20) DEFAULT ''PCS''',
                    target_schema);
            END IF;
            
            -- Add allow_decimal column if not exists
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'products' 
                  AND column_name = 'allow_decimal'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.products ADD COLUMN allow_decimal BOOLEAN DEFAULT false',
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
            
            -- Alter stock column to support decimals
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
            
            -- Alter min_stock_level column to support decimals
            IF EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'products' 
                  AND column_name = 'min_stock_level'
                  AND data_type = 'integer'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.products ALTER COLUMN min_stock_level TYPE DECIMAL(12,3) USING min_stock_level::DECIMAL(12,3)',
                    target_schema);
            END IF;
        END IF;
    END LOOP;
END $$;
