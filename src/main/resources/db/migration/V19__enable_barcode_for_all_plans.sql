-- ============================================================================
-- V19: Enable Barcode Scanning for All Subscription Plans
-- ============================================================================
-- Barcode scanning is now available on all plans (Starter, Business, Enterprise)
-- ============================================================================

-- Update STARTER plan to enable barcode scanning
UPDATE public.subscription_limits 
SET feature_barcode = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE plan_name = 'STARTER';

-- Add comment explaining the change
COMMENT ON COLUMN public.subscription_limits.feature_barcode IS 'Barcode scanning feature - available on all plans since V19';
