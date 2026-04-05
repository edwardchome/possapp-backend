package com.possapp.backend.exception;

import com.possapp.backend.entity.SubscriptionPlan;
import lombok.Getter;

/**
 * ============================================================================
 * SUBSCRIPTION ACCESS DENIED EXCEPTION
 * ============================================================================
 * Exception thrown when a user tries to access a feature or perform an action
 * that is not allowed due to subscription restrictions.
 * 
 * This can be due to:
 * - Plan level restrictions (feature not available on current plan)
 * - Soft lock (subscription expired, grace period ended)
 * - Hard suspension (payment fraud, terms violation)
 * ============================================================================
 */
@Getter
public class SubscriptionAccessDeniedException extends RuntimeException {
    
    private final SubscriptionPlan requiredPlan;
    private final SubscriptionPlan currentPlan;
    private final Integer remainingDays;
    private final boolean isSoftLock;
    
    /**
     * Constructor for plan level access denial.
     */
    public SubscriptionAccessDeniedException(String message, SubscriptionPlan requiredPlan, SubscriptionPlan currentPlan) {
        super(message);
        this.requiredPlan = requiredPlan;
        this.currentPlan = currentPlan;
        this.remainingDays = null;
        this.isSoftLock = false;
    }
    
    /**
     * Constructor for soft lock (expired subscription with ended grace period).
     */
    public SubscriptionAccessDeniedException(String message, String currentPlanName, int remainingDays) {
        super(message);
        this.requiredPlan = null;
        this.currentPlan = SubscriptionPlan.fromString(currentPlanName);
        this.remainingDays = remainingDays;
        this.isSoftLock = true;
    }
    
    /**
     * Constructor for hard suspension.
     */
    public SubscriptionAccessDeniedException(String message) {
        super(message);
        this.requiredPlan = null;
        this.currentPlan = null;
        this.remainingDays = null;
        this.isSoftLock = false;
    }
}
