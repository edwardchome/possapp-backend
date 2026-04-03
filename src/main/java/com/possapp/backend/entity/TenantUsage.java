package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ============================================================================
 * TENANT USAGE ENTITY
 * ============================================================================
 * Tracks the current resource usage for each tenant.
 * This is used to enforce subscription limits.
 * 
 * Usage counts are updated by the application when resources are created/deleted.
 * ============================================================================
 */
@Entity
@Table(name = "tenant_usage", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "current_users", nullable = false)
    @Builder.Default
    private Integer currentUsers = 0;

    @Column(name = "current_branches", nullable = false)
    @Builder.Default
    private Integer currentBranches = 0;

    @Column(name = "current_products", nullable = false)
    @Builder.Default
    private Integer currentProducts = 0;

    @Column(name = "current_monthly_transactions", nullable = false)
    @Builder.Default
    private Integer currentMonthlyTransactions = 0;

    @CreationTimestamp
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    // Helper methods

    /**
     * Get the current usage for a specific limit type.
     */
    public int getUsage(LimitType type) {
        return switch (type) {
            case USER -> currentUsers;
            case BRANCH -> currentBranches;
            case PRODUCT -> currentProducts;
            case MONTHLY_TRANSACTION -> currentMonthlyTransactions;
        };
    }

    /**
     * Increment usage for a specific limit type.
     */
    public void incrementUsage(LimitType type) {
        switch (type) {
            case USER -> currentUsers++;
            case BRANCH -> currentBranches++;
            case PRODUCT -> currentProducts++;
            case MONTHLY_TRANSACTION -> currentMonthlyTransactions++;
        }
    }

    /**
     * Decrement usage for a specific limit type.
     */
    public void decrementUsage(LimitType type) {
        switch (type) {
            case USER -> currentUsers = Math.max(0, currentUsers - 1);
            case BRANCH -> currentBranches = Math.max(0, currentBranches - 1);
            case PRODUCT -> currentProducts = Math.max(0, currentProducts - 1);
            case MONTHLY_TRANSACTION -> currentMonthlyTransactions = Math.max(0, currentMonthlyTransactions - 1);
        }
    }

    /**
     * Set usage for a specific limit type.
     */
    public void setUsage(LimitType type, int value) {
        switch (type) {
            case USER -> currentUsers = Math.max(0, value);
            case BRANCH -> currentBranches = Math.max(0, value);
            case PRODUCT -> currentProducts = Math.max(0, value);
            case MONTHLY_TRANSACTION -> currentMonthlyTransactions = Math.max(0, value);
        }
    }

    /**
     * Check if usage has reached the limit for a specific type.
     */
    public boolean isAtLimit(LimitType type, Integer limit) {
        return LimitType.isAtLimit(getUsage(type), limit);
    }

    /**
     * Reset monthly transaction count (call at start of each month).
     */
    public void resetMonthlyTransactions() {
        this.currentMonthlyTransactions = 0;
    }
}
