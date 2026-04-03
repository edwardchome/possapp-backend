package com.possapp.backend.exception;

import com.possapp.backend.entity.LimitType;
import com.possapp.backend.entity.SubscriptionPlan;
import lombok.Getter;

/**
 * ============================================================================
 * SUBSCRIPTION LIMIT EXCEEDED EXCEPTION
 * ============================================================================
 * Thrown when a tenant attempts to exceed their subscription limits.
 * Contains information about the limit, current usage, and upgrade options.
 * ============================================================================
 */
@Getter
public class SubscriptionLimitExceededException extends RuntimeException {

    private final String tenantId;
    private final LimitType limitType;
    private final int currentUsage;
    private final Integer limit;
    private final SubscriptionPlan currentPlan;
    private final SubscriptionPlan suggestedPlan;

    public SubscriptionLimitExceededException(String tenantId, LimitType limitType, 
                                               int currentUsage, Integer limit,
                                               SubscriptionPlan currentPlan,
                                               SubscriptionPlan suggestedPlan) {
        super(buildMessage(limitType, currentUsage, limit, currentPlan, suggestedPlan));
        this.tenantId = tenantId;
        this.limitType = limitType;
        this.currentUsage = currentUsage;
        this.limit = limit;
        this.currentPlan = currentPlan;
        this.suggestedPlan = suggestedPlan;
    }

    public SubscriptionLimitExceededException(String tenantId, LimitType limitType, 
                                               int currentUsage, Integer limit,
                                               SubscriptionPlan currentPlan) {
        this(tenantId, limitType, currentUsage, limit, currentPlan, 
             getSuggestedPlan(currentPlan, limitType));
    }

    private static String buildMessage(LimitType limitType, int currentUsage, 
                                       Integer limit, SubscriptionPlan currentPlan,
                                       SubscriptionPlan suggestedPlan) {
        String limitDisplay = limit == null || limit < 0 ? "unlimited" : String.valueOf(limit);
        return String.format(
            "Subscription limit exceeded: You have used %d out of %s %s on your %s plan. " +
            "Consider upgrading to the %s plan for more resources.",
            currentUsage, limitDisplay, limitType.getDisplayName(),
            currentPlan.getDisplayName(), suggestedPlan.getDisplayName()
        );
    }

    private static SubscriptionPlan getSuggestedPlan(SubscriptionPlan current, LimitType limitType) {
        // Suggest the next tier up
        return switch (current) {
            case STARTER -> SubscriptionPlan.BUSINESS;
            case BUSINESS -> SubscriptionPlan.ENTERPRISE;
            case ENTERPRISE -> SubscriptionPlan.ENTERPRISE; // Already at top
        };
    }

    /**
     * Get the error code for client-side handling.
     */
    public String getErrorCode() {
        return "SUBSCRIPTION_LIMIT_EXCEEDED_" + limitType.name();
    }

    /**
     * Get a user-friendly error message.
     */
    public String getUserMessage() {
        String limitDisplay = limit == null || limit < 0 ? "unlimited" : String.valueOf(limit);
        return String.format(
            "You've reached your %s limit (%s/%s) on the %s plan.",
            limitType.getDisplayName().toLowerCase(),
            currentUsage,
            limitDisplay,
            currentPlan.getDisplayName()
        );
    }
}
