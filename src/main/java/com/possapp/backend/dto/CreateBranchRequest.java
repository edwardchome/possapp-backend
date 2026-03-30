package com.possapp.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================================
 * CREATE BRANCH REQUEST - DTO for Creating New Branches
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBranchRequest {

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
    private boolean canSell = true;

    private String taxId;

    private String receiptHeader;

    private String receiptFooter;

    private String managerName;

    private String operatingHours;

    private String notes;
}
