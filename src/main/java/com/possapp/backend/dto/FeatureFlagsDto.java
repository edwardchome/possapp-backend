package com.possapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================================
 * FEATURE FLAGS DTO
 * ============================================================================
 * Represents feature availability for a subscription plan.
 * Used to enable/disable UI elements in the mobile app.
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeatureFlagsDto {

    private boolean basicPos;
    private boolean basicInventory;
    private boolean reports;
    private boolean barcode;
    private boolean analytics;
    private boolean multiBranch;
    private boolean userManagement;
    private boolean apiAccess;
    private boolean customIntegrations;
    private boolean prioritySupport;
    private boolean whiteLabel;

    /**
     * Create default feature flags for Starter plan.
     */
    public static FeatureFlagsDto starter() {
        return FeatureFlagsDto.builder()
                .basicPos(true)
                .basicInventory(true)
                .reports(false)
                .barcode(false)
                .analytics(false)
                .multiBranch(false)
                .userManagement(true) // Limited to 2 users
                .apiAccess(false)
                .customIntegrations(false)
                .prioritySupport(false)
                .whiteLabel(false)
                .build();
    }

    /**
     * Create default feature flags for Business plan.
     */
    public static FeatureFlagsDto business() {
        return FeatureFlagsDto.builder()
                .basicPos(true)
                .basicInventory(true)
                .reports(true)
                .barcode(true)
                .analytics(false)
                .multiBranch(true)
                .userManagement(true) // Limited to 5 users
                .apiAccess(false)
                .customIntegrations(false)
                .prioritySupport(false)
                .whiteLabel(false)
                .build();
    }

    /**
     * Create default feature flags for Enterprise plan.
     */
    public static FeatureFlagsDto enterprise() {
        return FeatureFlagsDto.builder()
                .basicPos(true)
                .basicInventory(true)
                .reports(true)
                .barcode(true)
                .analytics(true)
                .multiBranch(true)
                .userManagement(true) // Unlimited
                .apiAccess(true)
                .customIntegrations(true)
                .prioritySupport(true)
                .whiteLabel(true)
                .build();
    }
}
