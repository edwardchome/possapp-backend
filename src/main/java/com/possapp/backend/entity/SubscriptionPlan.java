package com.possapp.backend.entity;

/**
 * ============================================================================
 * SUBSCRIPTION PLAN ENUM
 * ============================================================================
 * Defines the three subscription tiers available in the PossApp system:
 * 
 * - STARTER: Free tier for small businesses (replaces FREE)
 *   * 1 branch maximum
 *   * 2 users maximum
 *   * Basic POS functionality only
 * 
 * - BUSINESS: Paid tier for growing businesses
 *   * Up to 3 branches
 *   * Up to 5 users
 *   * Reports and barcode scanning support
 *   * Multi-branch operations
 * 
 * - ENTERPRISE: Premium tier for large operations
 *   * Unlimited branches
 *   * Unlimited users
 *   * Advanced analytics
 *   * API access and custom integrations
 * ============================================================================
 */
public enum SubscriptionPlan {
    STARTER,
    BUSINESS,
    ENTERPRISE;

    /**
     * Check if this plan is at least the specified plan level.
     * Useful for feature gating (e.g., requires BUSINESS or higher).
     */
    public boolean isAtLeast(SubscriptionPlan other) {
        return this.ordinal() >= other.ordinal();
    }

    /**
     * Check if this plan has access to the specified plan's features.
     * Enterprise includes Business features, etc.
     */
    public boolean includes(SubscriptionPlan other) {
        return this.isAtLeast(other);
    }

    /**
     * Get display name for UI presentation.
     */
    public String getDisplayName() {
        return switch (this) {
            case STARTER -> "Starter";
            case BUSINESS -> "Business";
            case ENTERPRISE -> "Enterprise";
        };
    }

    /**
     * Get default plan (used for new tenants).
     */
    public static SubscriptionPlan getDefault() {
        return STARTER;
    }

    /**
     * Safely parse a string to SubscriptionPlan, returning default if invalid.
     */
    public static SubscriptionPlan fromString(String value) {
        if (value == null || value.isBlank()) {
            return getDefault();
        }
        try {
            return SubscriptionPlan.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            // Handle legacy "FREE" value
            if ("FREE".equalsIgnoreCase(value)) {
                return STARTER;
            }
            return getDefault();
        }
    }
}
