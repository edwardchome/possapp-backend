package com.possapp.backend.exception;

import lombok.Getter;

/**
 * ============================================================================
 * SOFT LOCK EXCEPTION
 * ============================================================================
 * Exception thrown when a tenant's subscription has expired and the grace
 * period has ended. The account is in read-only mode (soft lock).
 * 
 * SOFT LOCK BEHAVIOR:
 * - Read operations: ALLOWED (viewing data, reports, history)
 * - Write operations: BLOCKED (sales, adding products, users, inventory)
 * ============================================================================
 */
@Getter
public class SoftLockException extends RuntimeException {
    
    private final String currentPlan;
    private final int daysInSoftLock;
    private final String upgradeUrl;
    
    public SoftLockException(String message, String currentPlan, int daysInSoftLock) {
        super(message);
        this.currentPlan = currentPlan;
        this.daysInSoftLock = daysInSoftLock;
        this.upgradeUrl = "/subscription/upgrade";
    }
    
    public SoftLockException(String message, String currentPlan) {
        this(message, currentPlan, 0);
    }
}
