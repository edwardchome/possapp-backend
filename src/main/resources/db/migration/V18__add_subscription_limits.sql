-- ============================================================================
-- V18: Add Subscription Management System
-- ============================================================================
-- This migration adds comprehensive subscription management with 3 tiers:
-- - STARTER (replaces FREE): 1 branch, 2 users, basic features
-- - BUSINESS: 5 users, reports, barcode support
-- - ENTERPRISE: Unlimited users/branches, analytics + Business features
-- ============================================================================

-- Rename existing FREE plans to STARTER
UPDATE public.tenants 
SET subscription_plan = 'STARTER' 
WHERE subscription_plan = 'FREE' OR subscription_plan IS NULL;

-- Add subscription status column
ALTER TABLE public.tenants 
ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Add subscription period tracking
ALTER TABLE public.tenants 
ADD COLUMN IF NOT EXISTS subscription_started_at TIMESTAMP;

ALTER TABLE public.tenants 
ADD COLUMN IF NOT EXISTS current_period_end TIMESTAMP;

ALTER TABLE public.tenants 
ADD COLUMN IF NOT EXISTS trial_ends_at TIMESTAMP;

-- Add cancellation tracking
ALTER TABLE public.tenants 
ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;

ALTER TABLE public.tenants 
ADD COLUMN IF NOT EXISTS cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE;

-- Create subscription limits configuration table
CREATE TABLE IF NOT EXISTS public.subscription_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_name VARCHAR(20) NOT NULL UNIQUE,
    display_name VARCHAR(50) NOT NULL,
    description TEXT,
    max_users INTEGER NOT NULL,
    max_branches INTEGER NOT NULL,
    max_products INTEGER,
    max_monthly_transactions INTEGER,
    feature_reports BOOLEAN NOT NULL DEFAULT FALSE,
    feature_barcode BOOLEAN NOT NULL DEFAULT FALSE,
    feature_analytics BOOLEAN NOT NULL DEFAULT FALSE,
    feature_multi_branch BOOLEAN NOT NULL DEFAULT FALSE,
    feature_api_access BOOLEAN NOT NULL DEFAULT FALSE,
    feature_custom_integrations BOOLEAN NOT NULL DEFAULT FALSE,
    monthly_price DECIMAL(10, 2),
    yearly_price DECIMAL(10, 2),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on plan name for faster lookups
CREATE INDEX IF NOT EXISTS idx_subscription_limits_plan_name ON public.subscription_limits(plan_name);

-- Insert subscription plan configurations
INSERT INTO public.subscription_limits (
    plan_name, display_name, description, max_users, max_branches, max_products,
    feature_reports, feature_barcode, feature_analytics, feature_multi_branch,
    feature_api_access, feature_custom_integrations, monthly_price, yearly_price
) VALUES 
(
    'STARTER', 
    'Starter', 
    'Perfect for small businesses just getting started. Includes basic POS functionality.',
    2,      -- max_users
    1,      -- max_branches
    100,    -- max_products
    FALSE,  -- feature_reports
    FALSE,  -- feature_barcode
    FALSE,  -- feature_analytics
    FALSE,  -- feature_multi_branch
    FALSE,  -- feature_api_access
    FALSE,  -- feature_custom_integrations
    0.00,   -- monthly_price
    0.00    -- yearly_price
),
(
    'BUSINESS', 
    'Business', 
    'For growing businesses that need more users, reports, and barcode scanning support.',
    5,      -- max_users
    3,      -- max_branches
    1000,   -- max_products
    TRUE,   -- feature_reports
    TRUE,   -- feature_barcode
    FALSE,  -- feature_analytics
    TRUE,   -- feature_multi_branch
    FALSE,  -- feature_api_access
    FALSE,  -- feature_custom_integrations
    29.99,  -- monthly_price
    299.99  -- yearly_price
),
(
    'ENTERPRISE', 
    'Enterprise', 
    'Unlimited everything with advanced analytics, API access, and custom integrations.',
    -1,     -- max_users (unlimited)
    -1,     -- max_branches (unlimited)
    NULL,   -- max_products (unlimited)
    TRUE,   -- feature_reports
    TRUE,   -- feature_barcode
    TRUE,   -- feature_analytics
    TRUE,   -- feature_multi_branch
    TRUE,   -- feature_api_access
    TRUE,   -- feature_custom_integrations
    99.99,  -- monthly_price
    999.99  -- yearly_price
)
ON CONFLICT (plan_name) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    max_users = EXCLUDED.max_users,
    max_branches = EXCLUDED.max_branches,
    max_products = EXCLUDED.max_products,
    feature_reports = EXCLUDED.feature_reports,
    feature_barcode = EXCLUDED.feature_barcode,
    feature_analytics = EXCLUDED.feature_analytics,
    feature_multi_branch = EXCLUDED.feature_multi_branch,
    feature_api_access = EXCLUDED.feature_api_access,
    feature_custom_integrations = EXCLUDED.feature_custom_integrations,
    monthly_price = EXCLUDED.monthly_price,
    yearly_price = EXCLUDED.yearly_price,
    updated_at = CURRENT_TIMESTAMP;

-- Create tenant usage tracking table
CREATE TABLE IF NOT EXISTS public.tenant_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES public.tenants(id) ON DELETE CASCADE,
    current_users INTEGER NOT NULL DEFAULT 0,
    current_branches INTEGER NOT NULL DEFAULT 0,
    current_products INTEGER NOT NULL DEFAULT 0,
    current_monthly_transactions INTEGER NOT NULL DEFAULT 0,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id)
);

-- Create index on tenant_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_tenant_usage_tenant_id ON public.tenant_usage(tenant_id);

-- Initialize usage tracking for existing tenants
INSERT INTO public.tenant_usage (tenant_id, current_users, current_branches, current_products)
SELECT 
    t.id,
    0,  -- Will be calculated by application
    0,  -- Will be calculated by application
    0   -- Will be calculated by application
FROM public.tenants t
LEFT JOIN public.tenant_usage tu ON t.id = tu.tenant_id
WHERE tu.id IS NULL;

-- Create subscription audit log table
CREATE TABLE IF NOT EXISTS public.subscription_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES public.tenants(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL, -- UPGRADE, DOWNGRADE, CANCEL, REACTIVATE, etc.
    previous_plan VARCHAR(20),
    new_plan VARCHAR(20),
    previous_status VARCHAR(20),
    new_status VARCHAR(20),
    performed_by UUID, -- User ID who performed the action (null for system)
    reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on tenant_id and created_at for audit queries
CREATE INDEX IF NOT EXISTS idx_subscription_audit_tenant_id ON public.subscription_audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscription_audit_created_at ON public.subscription_audit_log(created_at);

-- Create function to update updated_at timestamp for subscription_limits
CREATE OR REPLACE FUNCTION update_subscription_limits_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger for subscription_limits
DROP TRIGGER IF EXISTS update_subscription_limits_updated_at ON public.subscription_limits;
CREATE TRIGGER update_subscription_limits_updated_at
    BEFORE UPDATE ON public.subscription_limits
    FOR EACH ROW
    EXECUTE FUNCTION update_subscription_limits_updated_at();

-- Update function for tenants to handle subscription_started_at
CREATE OR REPLACE FUNCTION handle_tenant_subscription_start()
RETURNS TRIGGER AS $$
BEGIN
    -- Set subscription_started_at when plan changes from NULL or STARTER to a paid plan
    IF (OLD.subscription_plan = 'STARTER' OR OLD.subscription_plan IS NULL) 
       AND NEW.subscription_plan IN ('BUSINESS', 'ENTERPRISE') 
       AND OLD.subscription_started_at IS NULL THEN
        NEW.subscription_started_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger for tenant subscription start tracking
DROP TRIGGER IF EXISTS handle_tenant_subscription_start ON public.tenants;
CREATE TRIGGER handle_tenant_subscription_start
    BEFORE UPDATE ON public.tenants
    FOR EACH ROW
    EXECUTE FUNCTION handle_tenant_subscription_start();

-- Update existing tenants to have subscription_started_at if they have a plan
UPDATE public.tenants 
SET subscription_started_at = created_at 
WHERE subscription_started_at IS NULL;

-- Add constraint to ensure valid subscription statuses
ALTER TABLE public.tenants 
DROP CONSTRAINT IF EXISTS chk_subscription_status;

ALTER TABLE public.tenants 
ADD CONSTRAINT chk_subscription_status 
CHECK (subscription_status IN ('ACTIVE', 'EXPIRED', 'CANCELLED', 'TRIAL', 'PAST_DUE', 'SUSPENDED'));

-- Add constraint to ensure valid subscription plans
ALTER TABLE public.tenants 
DROP CONSTRAINT IF EXISTS chk_subscription_plan;

ALTER TABLE public.tenants 
ADD CONSTRAINT chk_subscription_plan 
CHECK (subscription_plan IN ('STARTER', 'BUSINESS', 'ENTERPRISE'));
