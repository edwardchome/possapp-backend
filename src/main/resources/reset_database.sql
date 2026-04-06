-- ============================================================================
-- DATABASE RESET SCRIPT
-- ============================================================================
-- Run this to clear all tenant data and start fresh
-- WARNING: This will delete ALL tenant data!
-- ============================================================================

-- Start transaction
BEGIN;

-- Drop all tenant schemas
DO $$
DECLARE
    schema_name text;
BEGIN
    FOR schema_name IN
        SELECT schema_name FROM information_schema.schemata
        WHERE schema_name NOT IN ('public', 'pg_catalog', 'information_schema', 'pg_toast')
    LOOP
        EXECUTE 'DROP SCHEMA IF EXISTS ' || quote_ident(schema_name) || ' CASCADE';
        RAISE NOTICE 'Dropped schema: %', schema_name;
    END LOOP;
END $$;

-- Truncate all public tables
TRUNCATE TABLE public.tenant_usage CASCADE;
TRUNCATE TABLE public.tenants CASCADE;

-- Reset Flyway history (optional - uncomment if you want to re-run all migrations)
-- TRUNCATE TABLE public.flyway_schema_history;

COMMIT;

-- Verify cleanup
SELECT 'Remaining schemas:' as info;
SELECT schema_name FROM information_schema.schemata
WHERE schema_name NOT IN ('public', 'pg_catalog', 'information_schema', 'pg_toast');

SELECT 'Remaining tenants:' as info;
SELECT COUNT(*) as tenant_count FROM public.tenants;
