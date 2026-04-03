package com.possapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ============================================================================
 * SUBSCRIPTION DTO
 * ============================================================================
 * Represents a tenant's current subscription state.
 * Returned by the /api/v1/tenants/subscription endpoint.
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionDto {

    private String plan;
    private String status;
    private String displayName;
    
    // Period information
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime trialEndsAt;
    private Integer daysUntilExpiry;
    
    // Usage statistics
    private UsageDto usage;
    
    // Feature availability
    private FeatureFlagsDto features;
    
    // Actions
    private boolean canUpgrade;
    private boolean canCancel;
    private boolean isInTrial;
    private boolean needsRenewal;
    
    // Next plan suggestion (when at limits)
    private String suggestedPlan;
    private String suggestedPlanDisplayName;

    /**
     * Check if subscription is active and operational.
     */
    public boolean isActive() {
        return "ACTIVE".equals(status) || "TRIAL".equals(status);
    }

    /**
     * Check if subscription allows write operations.
     */
    public boolean canWrite() {
        return isActive() && !"EXPIRED".equals(status) && !"SUSPENDED".equals(status);
    }
}
