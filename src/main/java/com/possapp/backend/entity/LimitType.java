package com.possapp.backend.entity;

/**
 * ============================================================================
 * LIMIT TYPE ENUM
 * ============================================================================
 * Defines the types of resource limits that can be enforced for tenants.
 * Each limit type corresponds to a column in the subscription_limits table.
 * ============================================================================
 */
public enum LimitType {
    USER("Users", "max_users", "current_users"),
    BRANCH("Branches", "max_branches", "current_branches"),
    PRODUCT("Products", "max_products", "current_products"),
    MONTHLY_TRANSACTION("Monthly Transactions", "max_monthly_transactions", "current_monthly_transactions");

    private final String displayName;
    private final String configColumn;
    private final String usageColumn;

    LimitType(String displayName, String configColumn, String usageColumn) {
        this.displayName = displayName;
        this.configColumn = configColumn;
        this.usageColumn = usageColumn;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getConfigColumn() {
        return configColumn;
    }

    public String getUsageColumn() {
        return usageColumn;
    }

    /**
     * Check if a limit value represents "unlimited" (negative number or null).
     */
    public static boolean isUnlimited(Integer limit) {
        return limit == null || limit < 0;
    }

    /**
     * Check if current usage has exceeded the limit.
     */
    public static boolean isExceeded(int currentUsage, Integer limit) {
        if (isUnlimited(limit)) {
            return false;
        }
        return currentUsage >= limit;
    }

    /**
     * Check if current usage has reached the limit (for preventing new additions).
     */
    public static boolean isAtLimit(int currentUsage, Integer limit) {
        if (isUnlimited(limit)) {
            return false;
        }
        return currentUsage >= limit;
    }
}
