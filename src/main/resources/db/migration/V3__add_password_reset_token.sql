-- Add password_reset_token and password_reset_expiry columns to users table in all tenant schemas

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
        
        -- Check if users table exists in this schema
        IF EXISTS (
            SELECT 1 
            FROM information_schema.tables t
            WHERE t.table_schema = target_schema 
              AND t.table_name = 'users'
        ) THEN
            -- Add password_reset_token column if not exists
            IF NOT EXISTS (
                SELECT 1 
                FROM information_schema.columns c
                WHERE c.table_schema = target_schema 
                  AND c.table_name = 'users' 
                  AND c.column_name = 'password_reset_token'
            ) THEN
                EXECUTE format('ALTER TABLE %I.users ADD COLUMN password_reset_token VARCHAR(255)', target_schema);
                RAISE NOTICE 'Added password_reset_token column to schema: %', target_schema;
            END IF;
            
            -- Add password_reset_expiry column if not exists
            IF NOT EXISTS (
                SELECT 1 
                FROM information_schema.columns c
                WHERE c.table_schema = target_schema 
                  AND c.table_name = 'users' 
                  AND c.column_name = 'password_reset_expiry'
            ) THEN
                EXECUTE format('ALTER TABLE %I.users ADD COLUMN password_reset_expiry TIMESTAMP', target_schema);
                RAISE NOTICE 'Added password_reset_expiry column to schema: %', target_schema;
            END IF;
        END IF;
    END LOOP;
END $$;

-- Also ensure the columns exist in the public schema
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
          AND table_name = 'users'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
              AND table_name = 'users' 
              AND column_name = 'password_reset_token'
        ) THEN
            ALTER TABLE public.users ADD COLUMN password_reset_token VARCHAR(255);
        END IF;
        
        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
              AND table_name = 'users' 
              AND column_name = 'password_reset_expiry'
        ) THEN
            ALTER TABLE public.users ADD COLUMN password_reset_expiry TIMESTAMP;
        END IF;
    END IF;
END $$;
