package com.possapp.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ============================================================================
 * BRANCH DTO - Data Transfer Object for Branch Operations
 * ============================================================================
 *
 * Used for API requests and responses related to branch management.
 * Separates the entity from the API contract.
 *
 * VALIDATION RULES:
 * - name: Required, max 100 characters
 * - code: Optional, max 20 characters, should be unique
 * - address: Optional, max 500 characters
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDto {

    private String id;

    @NotBlank(message = "Branch name is required")
    @Size(max = 100, message = "Branch name must not exceed 100 characters")
    private String name;

    @Size(max = 20, message = "Branch code must not exceed 20 characters")
    private String code;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    private String phoneNumber;

    private String email;

    @Builder.Default
    private boolean mainBranch = false;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private boolean canSell = true;

    private String taxId;

    private String receiptHeader;

    private String receiptFooter;

    private String managerName;

    private String operatingHours;

    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String createdBy;
}
