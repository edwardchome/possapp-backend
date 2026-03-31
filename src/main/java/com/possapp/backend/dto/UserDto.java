package com.possapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {
    
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phoneNumber;
    private String role;
    private boolean canManageProducts;
    private boolean canManageInventory;
    private boolean active;
    private boolean emailVerified;
    private boolean passwordChangeRequired;
    private Long permissionsVersion;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    
    // Branch assignment
    private String branchId;
    private String branchName;
    
    // Active branch (for users with multiple branch access)
    private String activeBranchId;
    private String activeBranchName;
}
