package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.AuthRequest;
import com.possapp.backend.dto.AuthResponse;
import com.possapp.backend.dto.UserDto;
import com.possapp.backend.security.JwtTokenProvider;
import com.possapp.backend.service.EmailService;
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
    private final EmailService emailService;
    
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
    
    @PostMapping("/forgot-password")
    @Operation(
        summary = "Request password reset",
        description = "Send password reset email to user. Requires tenantId in request body."
    )
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Parameter(description = "Email address and tenantId", required = true)
            @RequestBody Map<String, String> request) {
        String email = request.get("email");
        String tenantId = request.get("tenantId");
        
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Email is required"));
        }
        
        if (tenantId == null || tenantId.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Tenant ID is required"));
        }
        
        try {
            // Set tenant context for this request
            TenantContext.setCurrentTenant(tenantId);
            
            // Generate token and send email
            String token = userService.generatePasswordResetToken(email);
            UserDto user = userService.getUserProfile(email);
            emailService.sendPasswordResetEmail(email, token, user.getFirstName(), tenantId);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Password reset email sent. Please check your inbox.", null));
        } catch (Exception e) {
            // Don't reveal if email exists for security
            log.warn("Password reset request failed for: {}", email);
            return ResponseEntity.ok(ApiResponse.success(
                "If an account exists with this email, you will receive a password reset link.", null));
        } finally {
            TenantContext.clear();
        }
    }
    
    @GetMapping("/reset-password")
    @Operation(
        summary = "Password reset page",
        description = "Returns instructions for password reset (for browser access)"
    )
    public ResponseEntity<String> resetPasswordPage(
            @Parameter(description = "Reset token", required = true)
            @RequestParam String token,
            @Parameter(description = "Tenant ID", required = true)
            @RequestParam String tenantId) {
        // Return HTML page with instructions to open the mobile app
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Reset Password - PossApp</title>
                <style>
                    body { font-family: Arial, sans-serif; background: #f5f5f5; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; }
                    .container { background: white; padding: 40px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); text-align: center; max-width: 400px; }
                    h1 { color: #4a6cf7; margin-bottom: 20px; }
                    p { color: #666; line-height: 1.6; margin-bottom: 20px; }
                    .button { display: inline-block; background: #4a6cf7; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; margin: 10px; }
                    .note { font-size: 12px; color: #999; margin-top: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🔐 Reset Password</h1>
                    <p>To reset your password, please open this link in the <strong>PossApp</strong> mobile application.</p>
                    <p>Token: <code>%s</code></p>
                    <p>Tenant: <code>%s</code></p>
                    <a href="possapp://reset-password?token=%s&tenantId=%s" class="button">Open in App</a>
                    <p class="note">If the button doesn't work, copy the token and enter it manually in the app's password reset screen.</p>
                </div>
            </body>
            </html>
            """.formatted(token, tenantId, token, tenantId);
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.TEXT_HTML).body(html);
    }
    
    @GetMapping("/reset-password/validate")
    @Operation(
        summary = "Validate password reset token",
        description = "Check if a password reset token is valid"
    )
    public ResponseEntity<ApiResponse<Boolean>> validateResetToken(
            @Parameter(description = "Reset token", required = true)
            @RequestParam String token) {
        boolean isValid = userService.isPasswordResetTokenValid(token);
        return ResponseEntity.ok(ApiResponse.success(isValid));
    }
    
    @PostMapping("/reset-password")
    @Operation(
        summary = "Reset password",
        description = "Reset password using token from email"
    )
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Parameter(description = "Reset request", required = true)
            @RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("newPassword");
        
        if (token == null || newPassword == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Token and new password are required"));
        }
        
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Password must be at least 6 characters"));
        }
        
        try {
            userService.resetPassword(token, newPassword);
            return ResponseEntity.ok(ApiResponse.success(
                "Password reset successful. Please login with your new password.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }
}
