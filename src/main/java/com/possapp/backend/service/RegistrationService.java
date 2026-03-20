package com.possapp.backend.service;

import com.possapp.backend.dto.*;
import com.possapp.backend.entity.PendingRegistration;
import com.possapp.backend.exception.TenantException;
import com.possapp.backend.exception.UserException;
import com.possapp.backend.repository.PendingRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {
    
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final EmailService emailService;
    private final TenantService tenantService;
    
    @Value("${app.verification.token-expiry-seconds:40}")
    private int verificationExpirySeconds;
    
    /**
     * Stage 1: Initiate registration with email only
     */
    @Transactional
    public Stage1Response initiateRegistration(String email) {
        // Normalize email
        email = email.toLowerCase().trim();
        
        // Check if email already has a pending registration
        Optional<PendingRegistration> existing = pendingRegistrationRepository.findByEmail(email);
        if (existing.isPresent()) {
            PendingRegistration pending = existing.get();
            if (pending.isEmailVerified()) {
                // Email already verified, skip to step 2
                return Stage1Response.builder()
                    .message("Email already verified. Proceed to complete your registration.")
                    .email(email)
                    .emailSent(false)
                    .emailAlreadyVerified(true)
                    .verificationToken(pending.getVerificationToken())
                    .build();
            }
            // Resend verification email
            sendVerificationEmail(pending);
            return Stage1Response.builder()
                .message("Verification email resent. Please check your inbox.")
                .email(email)
                .emailSent(true)
                .emailAlreadyVerified(false)
                .build();
        }
        
        // Check if email is already registered as a tenant admin
        if (tenantService.existsByAdminEmail(email)) {
            throw new TenantException("This email is already registered. Please login instead.");
        }
        
        // Create new pending registration
        PendingRegistration pending = PendingRegistration.builder()
            .email(email)
            .verificationToken(generateToken())
            .tokenExpiryTime(LocalDateTime.now().plusSeconds(verificationExpirySeconds))
            .emailVerified(false)
            .build();
        
        pendingRegistrationRepository.save(pending);
        
        // Send verification email
        sendVerificationEmail(pending);
        
        return Stage1Response.builder()
            .message("Verification email sent. Please check your inbox to continue.")
            .email(email)
            .emailSent(true)
            .emailAlreadyVerified(false)
            .build();
    }
    
    /**
     * Verify email with token
     */
    @Transactional
    public void verifyEmail(String token) {
        PendingRegistration pending = pendingRegistrationRepository.findByVerificationToken(token)
            .orElseThrow(() -> new UserException("Invalid verification token"));
        
        if (pending.isEmailVerified()) {
            log.info("Email already verified for: {}", pending.getEmail());
            return;
        }
        
        if (!pending.isTokenValid()) {
            throw new UserException("Verification token has expired. Please request a new one.");
        }
        
        pending.setEmailVerified(true);
        pending.setVerifiedAt(LocalDateTime.now());
        pendingRegistrationRepository.save(pending);
        
        log.info("Email verified for: {}", pending.getEmail());
    }
    
    /**
     * Resend verification email
     */
    @Transactional
    public void resendVerificationEmail(String email) {
        email = email.toLowerCase().trim();
        
        PendingRegistration pending = pendingRegistrationRepository.findByEmail(email)
            .orElseThrow(() -> new UserException("No pending registration found for this email"));
        
        if (pending.isEmailVerified()) {
            throw new UserException("Email is already verified");
        }
        
        // Generate new token
        pending.setVerificationToken(generateToken());
        pending.setTokenExpiryTime(LocalDateTime.now().plusSeconds(verificationExpirySeconds));
        pendingRegistrationRepository.save(pending);
        
        sendVerificationEmail(pending);
        
        log.info("Verification email resent to: {}", email);
    }
    
    /**
     * Stage 2: Complete registration after email verification
     */
    @Transactional
    public TenantDto completeRegistration(CompleteRegistrationRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        
        // Verify the email is verified
        PendingRegistration pending = pendingRegistrationRepository.findByEmail(email)
            .orElseThrow(() -> new TenantException("No pending registration found. Please start registration again."));
        
        if (!pending.isEmailVerified()) {
            throw new TenantException("Email not verified. Please verify your email first.");
        }
        
        // Validate the verification token matches
        if (!pending.getVerificationToken().equals(request.getVerificationToken())) {
            throw new TenantException("Invalid verification token.");
        }
        
        // Build the tenant registration request
        TenantRegistrationRequest tenantRequest = new TenantRegistrationRequest();
        tenantRequest.setCompanyName(request.getCompanyName());
        tenantRequest.setSchemaName(request.getSchemaName());
        tenantRequest.setAdminEmail(email);
        tenantRequest.setPassword(request.getPassword());
        tenantRequest.setContactPhone(request.getContactPhone());
        tenantRequest.setAddress(request.getAddress());
        tenantRequest.setSubscriptionPlan(request.getSubscriptionPlan());
        
        // Create tenant
        TenantDto tenant = tenantService.registerTenant(tenantRequest);
        
        // Clean up pending registration
        pendingRegistrationRepository.delete(pending);
        
        log.info("Registration completed for tenant: {} with email: {}", 
            request.getCompanyName(), email);
        
        return tenant;
    }
    
    /**
     * Check if email is verified
     */
    @Transactional(readOnly = true)
    public boolean isEmailVerified(String email) {
        return pendingRegistrationRepository.findByEmail(email.toLowerCase().trim())
            .map(PendingRegistration::isEmailVerified)
            .orElse(false);
    }
    
    /**
     * Get pending registration status
     */
    @Transactional(readOnly = true)
    public PendingRegistrationStatus getStatus(String email) {
        email = email.toLowerCase().trim();
        
        return pendingRegistrationRepository.findByEmail(email)
            .map(pending -> PendingRegistrationStatus.builder()
                .email(pending.getEmail())
                .emailVerified(pending.isEmailVerified())
                .verificationToken(pending.getVerificationToken())
                .tokenValid(pending.isTokenValid())
                .expiryTime(pending.getTokenExpiryTime())
                .build())
            .orElse(null);
    }
    
    private void sendVerificationEmail(PendingRegistration pending) {
        String verificationLink = buildVerificationLink(pending.getVerificationToken());
        emailService.sendHtmlEmail(
            pending.getEmail(),
            "Verify Your Email - PossApp",
            buildVerificationEmailBody(verificationLink, pending.getEmail())
        );
    }
    
    private String buildVerificationLink(String token) {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        // Use API endpoint directly - mobile app will handle this
        return baseUrl + "/api/v1/auth/verify-email?token=" + token;
    }
    
    private String buildVerificationEmailBody(String verificationLink, String email) {
        String link = verificationLink.replace("{email}", email);
        return String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <meta charset=\"UTF-8\">" +
            "    <style>" +
            "        body { font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }" +
            "        .container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 10px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
            "        .header { text-align: center; margin-bottom: 30px; }" +
            "        .header h1 { color: #4a6cf7; margin: 0; }" +
            "        .content { margin-bottom: 30px; }" +
            "        .button { display: inline-block; background-color: #4a6cf7; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; margin: 20px 0; }" +
            "        .info { background-color: #f8f9fa; padding: 15px; border-radius: 5px; margin: 15px 0; word-break: break-all; }" +
            "        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; }" +
            "    </style>" +
            "</head>" +
            "<body>" +
            "    <div class=\"container\">" +
            "        <div class=\"header\">" +
            "            <h1>Welcome to PossApp!</h1>" +
            "        </div>" +
            "        <div class=\"content\">" +
            "            <h2>Verify your email address</h2>" +
            "            <p>Thank you for starting your registration. Please click the button below to verify your email and continue:</p>" +
            "            <center><a href=\"%s\" class=\"button\">Verify Email & Continue</a></center>" +
            "            <p>Or copy and paste this link into your browser:</p>" +
            "            <div class=\"info\">%s</div>" +
            "            <p>This link will expire in 24 hours.</p>" +
            "        </div>" +
            "        <div class=\"footer\">" +
            "            <p>If you didn't start this registration, you can safely ignore this email.</p>" +
            "            <p>&copy; 2025 PossApp. All rights reserved.</p>" +
            "        </div>" +
            "    </div>" +
            "</body>" +
            "</html>",
            link, link
        );
    }
    
    private String generateToken() {
        return UUID.randomUUID().toString();
    }
}
