-- Add categories table to all existing tenant schemas

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
        
        -- Check if categories table exists
        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.tables t
            WHERE t.table_schema = target_schema 
              AND t.table_name = 'categories'
        ) THEN
            -- Create categories table
            EXECUTE format(
                'CREATE TABLE %I.categories (
                    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                    name VARCHAR(255) NOT NULL UNIQUE,
                    description TEXT,
                    display_order INTEGER DEFAULT 0,
                    is_active BOOLEAN NOT NULL DEFAULT true,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )', target_schema);
            
            -- Insert default General category
            EXECUTE format(
                'INSERT INTO %I.categories (id, name, description, display_order, is_active) VALUES (gen_random_uuid(), ''General'', ''Default category for products'', 0, true)',
                target_schema);
            
            RAISE NOTICE 'Created categories table in schema: %', target_schema;
        END IF;
        
        -- Check if products table has category_id column
        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns c
            WHERE c.table_schema = target_schema 
              AND c.table_name = 'products' 
              AND c.column_name = 'category_id'
        ) THEN
            -- Add category_id column if not exists
            EXECUTE format(
                'ALTER TABLE %I.products ADD COLUMN category_id VARCHAR(36)',
                target_schema);
            
            -- Add foreign key constraint
            EXECUTE format(
                'ALTER TABLE %I.products ADD CONSTRAINT fk_category 
                 FOREIGN KEY (category_id) REFERENCES %I.categories(id)',
                target_schema, target_schema);
            
            RAISE NOTICE 'Added category_id column to products in schema: %', target_schema;
        END IF;
    END LOOP;
END $$;
