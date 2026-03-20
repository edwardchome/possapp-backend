-- Add password_change_required column to users table in all tenant schemas
-- This migration handles existing tenant schemas created before this column was added

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
            -- Check if column already exists (to make migration idempotent)
            IF NOT EXISTS (
                SELECT 1 
                FROM information_schema.columns c
                WHERE c.table_schema = target_schema 
                  AND c.table_name = 'users' 
                  AND c.column_name = 'password_change_required'
            ) THEN
                -- Add the column
                EXECUTE format('ALTER TABLE %I.users ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT false', target_schema);
                RAISE NOTICE 'Added password_change_required column to schema: %', target_schema;
            ELSE
                RAISE NOTICE 'Column already exists in schema: %', target_schema;
            END IF;
        ELSE
            RAISE NOTICE 'No users table found in schema: %', target_schema;
        END IF;
    END LOOP;
END $$;

-- Also ensure the column exists in the public schema (for new tenant templates if needed)
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
              AND column_name = 'password_change_required'
        ) THEN
            ALTER TABLE public.users ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT false;
            RAISE NOTICE 'Added password_change_required column to public schema';
        END IF;
    END IF;
END $$;
