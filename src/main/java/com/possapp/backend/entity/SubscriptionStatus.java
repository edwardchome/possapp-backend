package com.possapp.backend.entity;

/**
 * ============================================================================
 * SUBSCRIPTION STATUS ENUM
 * ============================================================================
 * Tracks the current state of a tenant's subscription:
 * 
 * - ACTIVE: Subscription is valid and operational
 * - TRIAL: Trial period is active
 * - PAST_DUE: Payment failed but grace period active
 * - EXPIRED: Subscription has expired (soft limit - read-only access)
 * - CANCELLED: Subscription was cancelled by user
 * - SUSPENDED: Subscription suspended (hard limit - no access)
 * ============================================================================
 */
public enum SubscriptionStatus {
    ACTIVE("Active", true, true),
    TRIAL("Trial", true, true),
    PAST_DUE("Past Due", true, true),  // Grace period
    EXPIRED("Expired", true, false),   // Read-only access
    CANCELLED("Cancelled", true, false), // Read-only until period ends
    SUSPENDED("Suspended", false, false); // No access

    private final String displayName;
    private final boolean canRead;
    private final boolean canWrite;

    SubscriptionStatus(String displayName, boolean canRead, boolean canWrite) {
        this.displayName = displayName;
        this.canRead = canRead;
        this.canWrite = canWrite;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if the subscription allows read operations.
     */
    public boolean canRead() {
        return canRead;
    }

    /**
     * Check if the subscription allows write operations.
     */
    public boolean canWrite() {
        return canWrite;
    }

    /**
     * Check if this is an active operational status.
     */
    public boolean isOperational() {
        return this == ACTIVE || this == TRIAL;
    }

    /**
     * Check if the subscription needs payment attention.
     */
    public boolean needsAttention() {
        return this == PAST_DUE || this == EXPIRED;
    }

    /**
     * Check if the subscription is effectively cancelled.
     */
    public boolean isCancelled() {
        return this == CANCELLED || this == EXPIRED || this == SUSPENDED;
    }

    /**
     * Safely parse a string to SubscriptionStatus.
     */
    public static SubscriptionStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return SubscriptionStatus.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }
}
