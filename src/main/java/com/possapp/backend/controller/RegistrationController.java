package com.possapp.backend.controller;

import com.possapp.backend.dto.*;
import com.possapp.backend.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Registration", description = "Two-stage tenant registration APIs")
public class RegistrationController {
    
    private final RegistrationService registrationService;
    
    /**
     * Stage 1: Initiate registration with email
     */
    @PostMapping("/register/initiate")
    @Operation(
        summary = "Stage 1: Initiate registration",
        description = "Submit email to start registration. Verification email will be sent."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verification email sent"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email or already registered")
    })
    public ResponseEntity<ApiResponse<Stage1Response>> initiateRegistration(
            @Parameter(description = "Email address", required = true)
            @Valid @RequestBody Stage1Request request) {
        log.info("Registration initiated for email: {}", request.getEmail());
        
        Stage1Response response = registrationService.initiateRegistration(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }
    
    /**
     * Verify email (from email link)
     */
    @GetMapping("/verify-email")
    @Operation(
        summary = "Verify email address",
        description = "Verify email using token from verification email. Redirects to completion page."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Email verified successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired token")
    })
    public ResponseEntity<ApiResponse<String>> verifyEmail(
            @Parameter(description = "Verification token", required = true)
            @RequestParam String token) {
        log.info("Email verification attempt with token");
        
        registrationService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully. You can now complete your registration."));
    }
    
    /**
     * Resend verification email
     */
    @PostMapping("/resend-verification")
    @Operation(
        summary = "Resend verification email",
        description = "Request a new verification email for pending registration"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verification email resent"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "No pending registration found")
    })
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Parameter(description = "Email address", required = true)
            @RequestParam String email) {
        log.info("Resend verification requested for: {}", email);
        
        registrationService.resendVerificationEmail(email);
        return ResponseEntity.ok(ApiResponse.success("Verification email resent. Please check your inbox.", null));
    }
    
    /**
     * Check registration status
     */
    @GetMapping("/registration-status")
    @Operation(
        summary = "Check registration status",
        description = "Check if email is verified and ready for completion"
    )
    public ResponseEntity<ApiResponse<PendingRegistrationStatus>> checkStatus(
            @Parameter(description = "Email address", required = true)
            @RequestParam String email) {
        log.info("Checking registration status for: {}", email);
        
        PendingRegistrationStatus status = registrationService.getStatus(email);
        if (status == null) {
            return ResponseEntity.status(404)
                .body(ApiResponse.<PendingRegistrationStatus>error("No pending registration found for this email"));
        }
        return ResponseEntity.ok(ApiResponse.success(status));
    }
    
    /**
     * Stage 2: Complete registration
     */
    @PostMapping("/register/complete")
    @Operation(
        summary = "Stage 2: Complete registration",
        description = "Complete tenant registration after email verification"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Registration completed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or email not verified")
    })
    public ResponseEntity<ApiResponse<TenantDto>> completeRegistration(
            @Parameter(description = "Complete registration details", required = true)
            @Valid @RequestBody CompleteRegistrationRequest request) {
        log.info("Completing registration for: {}", request.getEmail());
        
        TenantDto tenant = registrationService.completeRegistration(request);
        return ResponseEntity.ok(ApiResponse.success("Registration completed successfully", tenant));
    }
}
