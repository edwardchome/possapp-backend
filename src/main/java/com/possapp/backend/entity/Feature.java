package com.possapp.backend.entity;

/**
 * ============================================================================
 * FEATURE ENUM
 * ============================================================================
 * Defines all premium features available in the PossApp system.
 * Each feature is associated with a minimum subscription plan required.
 * 
 * Feature availability by plan:
 * - STARTER: Basic POS, Barcode Scanning
 * - BUSINESS: Reports, Multi-branch
 * - ENTERPRISE: Analytics, API Access, Custom Integrations + all Business features
 * ============================================================================
 */
public enum Feature {
    // Basic features (available on all plans)
    BASIC_POS("Basic POS", "Point of sale operations", SubscriptionPlan.STARTER),
    BASIC_INVENTORY("Basic Inventory", "Inventory management", SubscriptionPlan.STARTER),
    BARCODE_SCANNING("Barcode Scanning", "Product barcode support", SubscriptionPlan.STARTER),
    
    // Business plan features
    REPORTS("Reports", "Sales and inventory reports", SubscriptionPlan.BUSINESS),
    MULTI_BRANCH("Multi-Branch", "Multiple branch support", SubscriptionPlan.BUSINESS),
    USER_MANAGEMENT("User Management", "Create and manage users", SubscriptionPlan.BUSINESS),
    
    // Enterprise plan features
    ANALYTICS("Analytics", "Advanced analytics and insights", SubscriptionPlan.ENTERPRISE),
    API_ACCESS("API Access", "External API access", SubscriptionPlan.ENTERPRISE),
    CUSTOM_INTEGRATIONS("Custom Integrations", "Third-party integrations", SubscriptionPlan.ENTERPRISE),
    PRIORITY_SUPPORT("Priority Support", "Priority customer support", SubscriptionPlan.ENTERPRISE),
    WHITE_LABEL("White Label", "Custom branding options", SubscriptionPlan.ENTERPRISE);

    private final String displayName;
    private final String description;
    private final SubscriptionPlan minimumPlan;

    Feature(String displayName, String description, SubscriptionPlan minimumPlan) {
        this.displayName = displayName;
        this.description = description;
        this.minimumPlan = minimumPlan;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public SubscriptionPlan getMinimumPlan() {
        return minimumPlan;
    }

    /**
     * Check if a given subscription plan has access to this feature.
     */
    public boolean isAvailableFor(SubscriptionPlan plan) {
        return plan.isAtLeast(minimumPlan);
    }

    /**
     * Get all features available for a given plan.
     */
    public static Feature[] getFeaturesForPlan(SubscriptionPlan plan) {
        return java.util.Arrays.stream(values())
                .filter(f -> f.isAvailableFor(plan))
                .toArray(Feature[]::new);
    }
}
