-- ============================================================================
-- V20: Add Trial and Grace Period Fields
-- ============================================================================
-- Adds fields for tracking trial expiration, grace period, and notifications
-- ============================================================================

-- Add new columns to tenants table
ALTER TABLE public.tenants
ADD COLUMN IF NOT EXISTS grace_period_ends_at TIMESTAMP;

ALTER TABLE public.tenants
ADD COLUMN IF NOT EXISTS trial_reminder_sent BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.tenants
ADD COLUMN IF NOT EXISTS trial_ended_notification_sent BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.tenants
ADD COLUMN IF NOT EXISTS grace_period_notification_sent BOOLEAN NOT NULL DEFAULT FALSE;

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_tenants_trial_ends_at 
ON public.tenants(trial_ends_at) 
WHERE subscription_status = 'TRIAL';

CREATE INDEX IF NOT EXISTS idx_tenants_grace_period_ends_at 
ON public.tenants(grace_period_ends_at) 
WHERE grace_period_ends_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tenants_subscription_status 
ON public.tenants(subscription_status);

-- Update existing tenants to have proper trial dates if they're in trial status
UPDATE public.tenants 
SET trial_ends_at = COALESCE(trial_ends_at, current_period_end, created_at + INTERVAL '14 days')
WHERE subscription_status = 'TRIAL' AND trial_ends_at IS NULL;

-- Update existing active tenants to have proper subscription started date
UPDATE public.tenants 
SET subscription_started_at = COALESCE(subscription_started_at, created_at)
WHERE subscription_started_at IS NULL;

-- Add comments explaining the fields
COMMENT ON COLUMN public.tenants.grace_period_ends_at IS 'End date of grace period after trial/subscription expiration';
COMMENT ON COLUMN public.tenants.trial_reminder_sent IS 'Whether 3-day trial reminder email was sent';
COMMENT ON COLUMN public.tenants.trial_ended_notification_sent IS 'Whether trial ended notification was sent';
COMMENT ON COLUMN public.tenants.grace_period_notification_sent IS 'Whether grace period ended notification was sent';
