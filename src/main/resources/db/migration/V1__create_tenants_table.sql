-- Create tenants table in public schema
CREATE TABLE IF NOT EXISTS public.tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(100) NOT NULL UNIQUE,
    admin_email VARCHAR(255) NOT NULL UNIQUE,
    contact_phone VARCHAR(50),
    address TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'FREE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subscription_expires_at TIMESTAMP
);

-- Create index on schema_name for faster lookups
CREATE INDEX IF NOT EXISTS idx_tenants_schema_name ON public.tenants(schema_name);

-- Create index on admin_email for faster lookups
CREATE INDEX IF NOT EXISTS idx_tenants_admin_email ON public.tenants(admin_email);

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger to automatically update updated_at
CREATE TRIGGER update_tenants_updated_at
    BEFORE UPDATE ON public.tenants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
