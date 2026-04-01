package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.CreateUserRequest;
import com.possapp.backend.dto.UserDto;
import com.possapp.backend.entity.Branch;
import com.possapp.backend.entity.User;
import com.possapp.backend.exception.UserException;
import com.possapp.backend.repository.BranchRepository;
import com.possapp.backend.repository.UserRepository;
import com.possapp.backend.service.EmailService;
import com.possapp.backend.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Management", description = "Manage users within a tenant (Admin only)")
public class UserController {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @GetMapping
    @Operation(
        summary = "List all users",
        description = "Get all users in the current tenant (Admin only)"
    )
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        String currentTenant = TenantContext.getCurrentTenant();
        log.info("Fetching all users for tenant: {}", currentTenant);

        List<User> users = userRepository.findAllWithBranch();
        List<UserDto> userDtos = users.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", userDtos));
    }

    @PostMapping
    @Operation(
        summary = "Create new user",
        description = "Create a new user in the current tenant (Admin only). Sends welcome email with login credentials."
    )
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> createUser(
            @Parameter(description = "User details", required = true)
            @Valid @RequestBody CreateUserRequest request) {
        
        String currentTenant = TenantContext.getCurrentTenant();
        log.info("Creating user: {} in tenant: {}", request.getEmail(), currentTenant);

        // Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new UserException("Email already exists: " + request.getEmail());
        }

        // Build user
        User.UserBuilder userBuilder = User.builder()
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .phoneNumber(request.getPhoneNumber())
            .role(request.getRole() != null ? request.getRole() : "USER")
            .canManageProducts(request.getCanManageProducts() != null ? request.getCanManageProducts() : false)
            .canManageInventory(request.getCanManageInventory() != null ? request.getCanManageInventory() : false)
            .emailVerified(true) // Admin-created users are pre-verified
            .active(true)
            .passwordChangeRequired(true); // Require password change on first login
        
        // Assign branch if provided
        if (request.getBranchId() != null && !request.getBranchId().isEmpty()) {
            Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new UserException("Branch not found: " + request.getBranchId()));
            userBuilder.branch(branch);
        }
        
        User user = userRepository.save(userBuilder.build());
        log.info("User created successfully: {} in tenant: {}", user.getEmail(), currentTenant);

        // Send welcome email with credentials
        try {
            emailService.sendWelcomeEmail(
                request.getEmail(),
                request.getPassword(),
                currentTenant,
                request.getFirstName()
            );
            log.info("Welcome email sent to: {}", request.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to: {}", request.getEmail(), e);
            // Don't fail user creation if email fails
        }
        
        // Reload user with branch data to avoid LazyInitializationException
        User createdUser = userRepository.findByIdWithBranch(user.getId())
            .orElse(user);

        return ResponseEntity.ok(ApiResponse.success("User created successfully. Welcome email sent.", mapToDto(createdUser)));
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get user by ID",
        description = "Get details of a specific user (Admin only)"
    )
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> getUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable String id) {
        
        User user = userRepository.findByIdWithBranch(id)
            .orElseThrow(() -> new UserException("User not found: " + id));

        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", mapToDto(user)));
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update user",
        description = "Update user details (Admin only). Changing role, permissions, or branch assignment will invalidate user's session."
    )
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Updated user details", required = true)
            @RequestBody CreateUserRequest request) {
        
        User user = userRepository.findByIdWithBranch(id)
            .orElseThrow(() -> new UserException("User not found: " + id));

        // Track if role, permissions, or branch assignment are being changed
        boolean roleChanged = request.getRole() != null && !request.getRole().equals(user.getRole());
        boolean productsPermissionChanged = request.getCanManageProducts() != null && 
                                            request.getCanManageProducts() != user.isCanManageProducts();
        boolean inventoryPermissionChanged = request.getCanManageInventory() != null && 
                                             request.getCanManageInventory() != user.isCanManageInventory();
        
        // Track branch assignment changes
        String currentBranchId = user.getBranch() != null ? user.getBranch().getId() : null;
        String newBranchId = request.getBranchId();
        boolean branchChanged = newBranchId != null && !newBranchId.equals(currentBranchId != null ? currentBranchId : "");
        boolean branchRemoved = newBranchId != null && newBranchId.isEmpty() && currentBranchId != null;
        
        boolean permissionsChanged = roleChanged || productsPermissionChanged || inventoryPermissionChanged || branchChanged || branchRemoved;

        // Update fields
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getCanManageProducts() != null) {
            user.setCanManageProducts(request.getCanManageProducts());
        }
        if (request.getCanManageInventory() != null) {
            user.setCanManageInventory(request.getCanManageInventory());
        }
        
        // Update branch assignment if provided
        Branch newBranch = null;
        if (request.getBranchId() != null) {
            if (request.getBranchId().isEmpty()) {
                // Empty string means remove branch assignment
                user.setBranch(null);
                user.setActiveBranch(null); // Also clear active branch
            } else {
                newBranch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new UserException("Branch not found: " + request.getBranchId()));
                user.setBranch(newBranch);
                // Also update activeBranch to the new branch so user sees the new branch's data immediately
                user.setActiveBranch(newBranch);
            }
        }

        // Increment permissions version if role, permissions, or branch changed
        // This will force the user to logout and login again
        if (permissionsChanged) {
            user.setPermissionsVersion(user.getPermissionsVersion() + 1);
            String changeType = branchChanged || branchRemoved ? "branch assignment" : "permissions";
            log.info("User {} changed for: {}. New version: {}. User will be forced to logout.", 
                     changeType, user.getEmail(), user.getPermissionsVersion());
        }

        user = userRepository.save(user);
        log.info("User updated: {}", user.getEmail());
        
        // Reload user with branch data to avoid LazyInitializationException
        User updatedUser = userRepository.findByIdWithBranch(user.getId())
            .orElse(user);

        String changeReason = "";
        if (permissionsChanged) {
            if (branchChanged || branchRemoved) {
                changeReason = ". User's session has been invalidated due to branch assignment changes.";
            } else {
                changeReason = ". User's session has been invalidated due to permission changes.";
            }
        }
        
        return ResponseEntity.ok(ApiResponse.success("User updated successfully" + changeReason, 
            mapToDto(updatedUser)));
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete user",
        description = "Soft delete (deactivate) a user (Admin only)"
    )
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable String id,
            Authentication authentication) {
        
        User user = userRepository.findByIdWithBranch(id)
            .orElseThrow(() -> new UserException("User not found: " + id));

        // Prevent deleting yourself
        if (user.getEmail().equals(authentication.getName())) {
            throw new UserException("Cannot delete your own account");
        }

        user.setActive(false);
        userRepository.save(user);
        log.info("User deactivated: {}", user.getEmail());

        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully", null));
    }

    @PostMapping("/{id}/activate")
    @Operation(
        summary = "Activate user",
        description = "Reactivate a deactivated user (Admin only)"
    )
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> activateUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable String id) {
        
        User user = userRepository.findByIdWithBranch(id)
            .orElseThrow(() -> new UserException("User not found: " + id));

        user.setActive(true);
        user = userRepository.save(user);
        log.info("User activated: {}", user.getEmail());
        
        // Reload user with branch data to avoid LazyInitializationException
        User updatedUser = userRepository.findByIdWithBranch(user.getId())
            .orElse(user);

        return ResponseEntity.ok(ApiResponse.success("User activated successfully", mapToDto(updatedUser)));
    }

    @PostMapping("/{id}/reset-password")
    @Operation(
        summary = "Reset user password",
        description = "Reset a user's password (Admin only)"
    )
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Parameter(description = "User ID", required = true)
            @PathVariable String id,
            @Parameter(description = "New password", required = true)
            @RequestParam String newPassword) {
        
        if (newPassword.length() < 6) {
            throw new UserException("Password must be at least 6 characters");
        }

        User user = userRepository.findByIdWithBranch(id)
            .orElseThrow(() -> new UserException("User not found: " + id));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password reset for user: {}", user.getEmail());

        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
    }
    
    @PostMapping("/me/active-branch")
    @Operation(
        summary = "Set active branch for current user",
        description = "Update the currently logged-in user's active branch for the session"
    )
    @Transactional
    public ResponseEntity<ApiResponse<UserDto>> setActiveBranch(
            @Parameter(description = "Branch ID to set as active", required = true)
            @RequestBody SetActiveBranchRequest request,
            Authentication authentication) {
        
        String email = authentication.getName();
        log.info("Setting active branch for user: {} to branch: {}", email, request.getBranchId());
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UserException("User not found"));
        
        // Validate the branch exists and is active
        Branch branch = branchRepository.findById(request.getBranchId())
            .orElseThrow(() -> new UserException("Branch not found: " + request.getBranchId()));
        
        if (!branch.isActive()) {
            throw new UserException("Branch is not active: " + branch.getName());
        }
        
        // Check if user is allowed to use this branch
        // Admins and Managers can switch to any branch
        // Regular users with a specific branch assignment can only switch to that branch
        // Users with no branch assignment (null) can switch to any branch
        boolean isAdminOrManager = user.getRole() != null && 
            (user.getRole().equals("ADMIN") || user.getRole().equals("MANAGER"));
        
        if (!isAdminOrManager && user.getBranch() != null && 
            !user.getBranch().getId().equals(request.getBranchId())) {
            throw new UserException("You are not assigned to this branch");
        }
        
        user.setActiveBranch(branch);
        user = userRepository.save(user);
        
        // Reload user with branch data eagerly loaded to avoid LazyInitializationException
        User updatedUser = userRepository.findByIdWithBranch(user.getId())
            .orElse(user);
        
        log.info("Active branch set for user: {} to branch: {}", email, branch.getName());
        return ResponseEntity.ok(ApiResponse.success("Active branch updated", mapToDto(updatedUser)));
    }
    
    // Request DTO for setting active branch
    @lombok.Data
    public static class SetActiveBranchRequest {
        private String branchId;
    }

    private UserDto mapToDto(User user) {
        UserDto.UserDtoBuilder builder = UserDto.builder()
            .id(user.getId())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .fullName(user.getFirstName() != null && user.getLastName() != null 
                ? user.getFirstName() + " " + user.getLastName() 
                : user.getEmail())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole())
            .canManageProducts(user.isCanManageProducts())
            .canManageInventory(user.isCanManageInventory())
            .emailVerified(user.isEmailVerified())
            .active(user.isActive())
            .passwordChangeRequired(user.isPasswordChangeRequired())
            .lastLoginAt(user.getLastLoginAt())
            .createdAt(user.getCreatedAt());
        
        // Include branch info if assigned
        if (user.getBranch() != null) {
            builder.branchId(user.getBranch().getId());
            builder.branchName(user.getBranch().getName());
        }
        
        // Include active branch info
        if (user.getActiveBranch() != null) {
            builder.activeBranchId(user.getActiveBranch().getId());
            builder.activeBranchName(user.getActiveBranch().getName());
        }
        
        return builder.build();
    }
}
