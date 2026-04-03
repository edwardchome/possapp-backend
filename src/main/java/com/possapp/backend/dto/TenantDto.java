package com.possapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ============================================================================
 * TENANT DTO
 * ============================================================================
 * Data transfer object for tenant (business) information.
 * Excludes internal fields and sensitive data.
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantDto {
    
    private UUID id;
    private String companyName;
    private String schemaName;
    private String adminEmail;
    private String contactPhone;
    private String address;
    private boolean active;
    
    // Subscription fields
    private String subscriptionPlan;
    private String subscriptionStatus;
    private LocalDateTime subscriptionStartedAt;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime trialEndsAt;
    private boolean cancelAtPeriodEnd;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime subscriptionExpiresAt;
}
