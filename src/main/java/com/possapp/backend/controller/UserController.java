package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.CreateUserRequest;
import com.possapp.backend.dto.UserDto;
import com.possapp.backend.entity.User;
import com.possapp.backend.exception.UserException;
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

        List<User> users = userRepository.findAll();
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

        // Create new user
        User user = User.builder()
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .phoneNumber(request.getPhoneNumber())
            .role(request.getRole() != null ? request.getRole() : "USER")
            .emailVerified(true) // Admin-created users are pre-verified
            .active(true)
            .passwordChangeRequired(true) // Require password change on first login
            .build();

        user = userRepository.save(user);
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

        return ResponseEntity.ok(ApiResponse.success("User created successfully. Welcome email sent.", mapToDto(user)));
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
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserException("User not found: " + id));

        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", mapToDto(user)));
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update user",
        description = "Update user details (Admin only)"
    )
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Updated user details", required = true)
            @RequestBody CreateUserRequest request) {
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserException("User not found: " + id));

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

        user = userRepository.save(user);
        log.info("User updated: {}", user.getEmail());

        return ResponseEntity.ok(ApiResponse.success("User updated successfully", mapToDto(user)));
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
        
        User user = userRepository.findById(id)
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
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserException("User not found: " + id));

        user.setActive(true);
        user = userRepository.save(user);
        log.info("User activated: {}", user.getEmail());

        return ResponseEntity.ok(ApiResponse.success("User activated successfully", mapToDto(user)));
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

        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserException("User not found: " + id));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password reset for user: {}", user.getEmail());

        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
    }

    private UserDto mapToDto(User user) {
        return UserDto.builder()
            .id(user.getId())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .fullName(user.getFirstName() != null && user.getLastName() != null 
                ? user.getFirstName() + " " + user.getLastName() 
                : user.getEmail())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole())
            .emailVerified(user.isEmailVerified())
            .active(user.isActive())
            .passwordChangeRequired(user.isPasswordChangeRequired())
            .lastLoginAt(user.getLastLoginAt())
            .createdAt(user.getCreatedAt())
            .build();
    }
}
