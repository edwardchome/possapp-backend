-- Create units_of_measure table for all tenant schemas

-- Create a function to create the table in a specific schema
CREATE OR REPLACE FUNCTION create_units_table_in_schema(schema_name text)
RETURNS void AS $$
BEGIN
    -- Check if the schema exists
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = $1) THEN
        -- Create the units_of_measure table if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = $1 AND table_name = 'units_of_measure'
        ) THEN
            EXECUTE format('
                CREATE TABLE %I.units_of_measure (
                    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                    name VARCHAR(255) NOT NULL,
                    symbol VARCHAR(50) NOT NULL UNIQUE,
                    type VARCHAR(20) NOT NULL,
                    description TEXT,
                    allow_fractions BOOLEAN NOT NULL DEFAULT false,
                    default_precision INTEGER DEFAULT 0,
                    is_active BOOLEAN NOT NULL DEFAULT true,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            ', $1);
            
            -- Insert default units
            EXECUTE format('
                INSERT INTO %I.units_of_measure (name, symbol, type, description, allow_fractions, default_precision, is_active) VALUES
                    (''Pieces'', ''pcs'', ''COUNT'', ''Individual items'', false, 0, true),
                    (''Kilogram'', ''kg'', ''WEIGHT'', ''Weight in kilograms'', true, 3, true),
                    (''Gram'', ''g'', ''WEIGHT'', ''Weight in grams'', true, 0, true),
                    (''Liter'', ''LT'', ''VOLUME'', ''Volume in liters'', true, 2, true),
                    (''Milliliter'', ''ml'', ''VOLUME'', ''Volume in milliliters'', true, 0, true),
                    (''Meter'', ''m'', ''LENGTH'', ''Length in meters'', true, 2, true),
                    (''Centimeter'', ''cm'', ''LENGTH'', ''Length in centimeters'', true, 1, true),
                    (''Square Meter'', ''m²'', ''AREA'', ''Area in square meters'', true, 2, true)
            ', $1);
        END IF;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Apply to all tenant schemas (exclude public, information_schema, pg_*)
DO $$
DECLARE
    schema_record RECORD;
BEGIN
    FOR schema_record IN 
        SELECT schema_name 
        FROM information_schema.schemata 
        WHERE schema_name NOT IN ('public', 'information_schema')
        AND schema_name NOT LIKE 'pg_%'
    LOOP
        PERFORM create_units_table_in_schema(schema_record.schema_name);
    END LOOP;
END $$;

-- Clean up the function
DROP FUNCTION IF EXISTS create_units_table_in_schema(text);
