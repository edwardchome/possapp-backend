package com.possapp.backend.service;

import com.possapp.backend.entity.User;
import com.possapp.backend.exception.UserException;
import com.possapp.backend.repository.UserRepository;
import com.possapp.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    
    private final UserRepository userRepository;
    private final EmailService emailService;
    
    @Value("${app.email.verification.expiry-hours:24}")
    private int verificationExpiryHours;
    
    /**
     * Generate and send email verification token
     */
    @Transactional
    public void sendVerificationEmail(String email, String tenantId) {
        try {
            // Set tenant context
            TenantContext.setCurrentTenant(tenantId);
            
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException("User not found"));
            
            // Generate verification token
            String token = UUID.randomUUID().toString();
            user.setEmailVerificationToken(token);
            user.setEmailVerificationExpiry(LocalDateTime.now().plusHours(verificationExpiryHours));
            userRepository.save(user);
            
            // Send email
            emailService.sendVerificationEmail(email, tenantId, token);
            
            log.info("Verification email sent to: {} in tenant: {}", email, tenantId);
        } finally {
            TenantContext.clear();
        }
    }
    
    /**
     * Verify email with token
     */
    @Transactional
    public void verifyEmail(String token, String tenantId) {
        try {
            // Set tenant context
            TenantContext.setCurrentTenant(tenantId);
            
            User user = userRepository.findAll().stream()
                .filter(u -> token.equals(u.getEmailVerificationToken()))
                .findFirst()
                .orElseThrow(() -> new UserException("Invalid verification token"));
            
            if (!user.isEmailVerificationTokenValid()) {
                throw new UserException("Verification token has expired");
            }
            
            user.setEmailVerified(true);
            user.setEmailVerificationToken(null);
            user.setEmailVerificationExpiry(null);
            userRepository.save(user);
            
            log.info("Email verified for user: {} in tenant: {}", user.getEmail(), tenantId);
        } finally {
            TenantContext.clear();
        }
    }
    
    /**
     * Resend verification email
     */
    @Transactional
    public void resendVerificationEmail(String email, String tenantId) {
        try {
            // Set tenant context
            TenantContext.setCurrentTenant(tenantId);
            
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException("User not found"));
            
            if (user.isEmailVerified()) {
                throw new UserException("Email is already verified");
            }
            
            // Generate new token
            String token = UUID.randomUUID().toString();
            user.setEmailVerificationToken(token);
            user.setEmailVerificationExpiry(LocalDateTime.now().plusHours(verificationExpiryHours));
            userRepository.save(user);
            
            // Send email
            emailService.sendVerificationEmail(email, tenantId, token);
            
            log.info("Verification email resent to: {} in tenant: {}", email, tenantId);
        } finally {
            TenantContext.clear();
        }
    }
    
    /**
     * Check if email is verified
     */
    @Transactional(readOnly = true)
    public boolean isEmailVerified(String email, String tenantId) {
        // Save current tenant context
        String previousTenant = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(tenantId);
            
            return userRepository.findByEmail(email)
                .map(User::isEmailVerified)
                .orElse(false);
        } finally {
            // Restore previous tenant context instead of clearing
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
            }
            // Note: We don't clear here because we might be in the middle of a request
            // that needs the tenant context. The TenantFilter will clear at the end.
        }
    }
}
