-- Fix receipt_items qty column to support decimal/fractional quantities
-- Changes qty from INTEGER to DECIMAL(15,4)

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
        
        -- Check if receipt_items table exists in this schema
        IF EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = target_schema AND table_name = 'receipt_items'
        ) THEN
            -- Alter qty column to DECIMAL(15,4)
            IF EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'receipt_items' 
                  AND column_name = 'qty'
                  AND data_type = 'integer'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.receipt_items ALTER COLUMN qty TYPE DECIMAL(15,4) USING qty::DECIMAL(15,4)',
                    target_schema);
            END IF;
            
            -- Add qty column if not exists (with DECIMAL type)
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = target_schema 
                  AND table_name = 'receipt_items' 
                  AND column_name = 'qty'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I.receipt_items ADD COLUMN qty DECIMAL(15,4) NOT NULL DEFAULT 0',
                    target_schema);
            END IF;
        END IF;
    END LOOP;
END $$;
