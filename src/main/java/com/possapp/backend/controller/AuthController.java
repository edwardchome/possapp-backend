package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.AuthRequest;
import com.possapp.backend.dto.AuthResponse;
import com.possapp.backend.dto.UserDto;
import com.possapp.backend.security.JwtTokenProvider;
import com.possapp.backend.service.EmailVerificationService;
import com.possapp.backend.service.UserService;
import com.possapp.backend.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and user management APIs")
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    
    @PostMapping("/login")
    @Operation(
        summary = "Login",
        description = "Authenticate user and return JWT token. Requires X-Tenant-ID header for tenant context. Email verification is required."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful", 
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Email not verified"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Parameter(description = "Login credentials", required = true) 
            @Valid @RequestBody AuthRequest request) {
        log.info("Login attempt for: {}", request.getEmail());
        
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // Get current tenant ID for token
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.error("No tenant context found during login for user: {}", request.getEmail());
            return ResponseEntity.status(400)
                .body(ApiResponse.<AuthResponse>error("Tenant context required"));
        }
        
        // Check if email is verified
        if (!emailVerificationService.isEmailVerified(request.getEmail(), tenantId)) {
            log.warn("Login attempt for unverified email: {}", request.getEmail());
            return ResponseEntity.status(403)
                .body(ApiResponse.<AuthResponse>error("Email not verified. Please check your email and verify your account."));
        }
        
        String token = jwtTokenProvider.generateToken(authentication, tenantId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(request.getEmail(), tenantId);
        
        userService.updateLastLogin(request.getEmail());
        UserDto userDto = userService.getUserProfile(request.getEmail());
        
        AuthResponse authResponse = AuthResponse.builder()
            .token(token)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getExpirationTime() / 1000)
            .user(userDto)
            .build();
        
        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }
    
    @PostMapping("/register")
    @Operation(
        summary = "Register new user",
        description = "Register a new user in the current tenant. Requires X-Tenant-ID header."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Registration successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "User already exists or invalid data")
    })
    @Transactional
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Parameter(description = "Registration details", required = true)
            @Valid @RequestBody AuthRequest request) {
        log.info("Registration attempt for: {}", request.getEmail());
        
        // Encode password before saving
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        var user = userService.createUser(request.getEmail(), encodedPassword, null, null);
        
        try {
            // Authenticate the newly created user
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Get current tenant ID for token
            String tenantId = TenantContext.getCurrentTenant();
            if (tenantId == null) {
                log.error("No tenant context found during registration for user: {}", request.getEmail());
                return ResponseEntity.status(400)
                    .body(ApiResponse.<AuthResponse>error("Tenant context required"));
            }
            
            String token = jwtTokenProvider.generateToken(authentication, tenantId);
            String refreshToken = jwtTokenProvider.generateRefreshToken(request.getEmail(), tenantId);
            
            AuthResponse authResponse = AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationTime() / 1000)
                .user(userService.mapToDto(user))
                .build();
            
            return ResponseEntity.ok(ApiResponse.success("Registration successful", authResponse));
            
        } catch (BadCredentialsException e) {
            log.error("Authentication failed for newly created user: {}", request.getEmail());
            // Transaction will rollback since exception propagates
            throw new BadCredentialsException("Authentication failed after registration");
        }
    }
    
    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh token",
        description = "Get new access token using refresh token. Requires X-Tenant-ID header."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Parameter(description = "Refresh token request", required = true)
            @RequestBody Map<String, String> request,
            @Parameter(description = "Tenant ID", required = true)
            @RequestHeader("X-Tenant-ID") String tenantId) {
        String refreshToken = request.get("refreshToken");
        
        // Validate that the refresh token belongs to the requesting tenant
        if (!jwtTokenProvider.validateTokenForTenant(refreshToken, tenantId)) {
            return ResponseEntity.status(401)
                .body(ApiResponse.error("Invalid refresh token or tenant mismatch"));
        }
        
        String email = jwtTokenProvider.getUsernameFromToken(refreshToken);
        String newToken = jwtTokenProvider.generateToken(email, tenantId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email, tenantId);
        
        UserDto userDto = userService.getUserProfile(email);
        
        AuthResponse authResponse = AuthResponse.builder()
            .token(newToken)
            .refreshToken(newRefreshToken)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getExpirationTime() / 1000)
            .user(userDto)
            .build();
        
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", authResponse));
    }
    
    @GetMapping("/validate")
    @Operation(
        summary = "Validate session",
        description = "Check if current session is valid",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Boolean>> validateSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isValid = authentication != null && authentication.isAuthenticated();
        return ResponseEntity.ok(ApiResponse.success(isValid));
    }
    
    @PostMapping("/logout")
    @Operation(
        summary = "Logout",
        description = "Clear current authentication context",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> logout() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }
    
    @GetMapping("/profile")
    @Operation(
        summary = "Get user profile",
        description = "Get current authenticated user's profile",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<UserDto>> getProfile() {
        UserDto user = userService.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401)
                .body(ApiResponse.error("Not authenticated"));
        }
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    @PutMapping("/profile")
    @Operation(
        summary = "Update profile",
        description = "Update current user's profile",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(
            @Parameter(description = "Profile data to update")
            @RequestBody Map<String, Object> profileData) {
        UserDto currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401)
                .body(ApiResponse.error("Not authenticated"));
        }
        
        UserDto updatedUser = userService.updateUserProfile(currentUser.getEmail(), profileData);
        return ResponseEntity.ok(ApiResponse.success("Profile updated", updatedUser));
    }
    
    @DeleteMapping("/account")
    @Operation(
        summary = "Delete account",
        description = "Delete current user's account",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> deleteAccount() {
        UserDto currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401)
                .body(ApiResponse.error("Not authenticated"));
        }
        
        userService.deleteUser(currentUser.getEmail());
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(ApiResponse.success("Account deleted", null));
    }
    
    @PostMapping("/change-password")
    @Operation(
        summary = "Change password",
        description = "Change user password. Clears passwordChangeRequired flag on first login.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Parameter(description = "Password change request", required = true)
            @RequestBody Map<String, String> request) {
        UserDto currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401)
                .body(ApiResponse.error("Not authenticated"));
        }
        
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");
        
        if (currentPassword == null || newPassword == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Current password and new password are required"));
        }
        
        userService.changePassword(currentUser.getEmail(), currentPassword, newPassword);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }
}
