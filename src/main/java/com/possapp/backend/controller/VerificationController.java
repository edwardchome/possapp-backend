package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/verify")
@RequiredArgsConstructor
@Tag(name = "Email Verification", description = "Email verification APIs")
public class VerificationController {
    
    private final EmailVerificationService verificationService;
    
    @GetMapping("/email")
    @Operation(
        summary = "Verify email address",
        description = "Verify email using the token sent via email"
    )
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Parameter(description = "Verification token", required = true)
            @RequestParam String token,
            @Parameter(description = "Tenant ID", required = true)
            @RequestParam String tenant) {
        
        log.info("Email verification attempt for tenant: {}", tenant);
        
        try {
            verificationService.verifyEmail(token, tenant);
            return ResponseEntity.ok(ApiResponse.success("Email verified successfully", null));
        } catch (Exception e) {
            log.error("Email verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    @PostMapping("/email/resend")
    @Operation(
        summary = "Resend verification email",
        description = "Resend the verification email to the user"
    )
    public ResponseEntity<ApiResponse<Void>> resendVerificationEmail(
            @Parameter(description = "Email address", required = true)
            @RequestParam String email,
            @Parameter(description = "Tenant ID", required = true)
            @RequestParam String tenant) {
        
        log.info("Resending verification email to: {} in tenant: {}", email, tenant);
        
        try {
            verificationService.resendVerificationEmail(email, tenant);
            return ResponseEntity.ok(ApiResponse.success("Verification email sent", null));
        } catch (Exception e) {
            log.error("Failed to resend verification email: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    @GetMapping("/email/status")
    @Operation(
        summary = "Check email verification status",
        description = "Check if a user's email is verified"
    )
    public ResponseEntity<ApiResponse<Boolean>> checkVerificationStatus(
            @Parameter(description = "Email address", required = true)
            @RequestParam String email,
            @Parameter(description = "Tenant ID", required = true)
            @RequestParam String tenant) {
        
        boolean isVerified = verificationService.isEmailVerified(email, tenant);
        return ResponseEntity.ok(ApiResponse.success(isVerified));
    }
}
