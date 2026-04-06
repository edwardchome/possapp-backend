package com.possapp.backend.service;

import com.possapp.backend.config.TrialDebugConfig;
import com.possapp.backend.entity.SubscriptionStatus;
import com.possapp.backend.entity.Tenant;
import com.possapp.backend.entity.TenantUsage;
import com.possapp.backend.repository.TenantRepository;
import com.possapp.backend.repository.TenantUsageRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * ============================================================================
 * TRIAL EXPIRATION SERVICE
 * ============================================================================
 * Handles trial period management including:
 * - Daily checks for trial expirations
 * - Email notifications (3 days before, 1 day before, on expiration)
 * - Grace period management (7 days after trial ends)
 * - Automatic status transitions
 * - Soft lock enforcement
 * ============================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrialExpirationService {

    private final TenantRepository tenantRepository;
    private final TenantUsageRepository tenantUsageRepository;
    private final EmailService emailService;
    private final SubscriptionService subscriptionService;
    private final TrialDebugConfig debugConfig;

    // Production notification thresholds (days before expiration)
    private static final int PROD_REMINDER_3_DAYS = 3;
    private static final int PROD_REMINDER_1_DAY = 1;
    
    // Production grace period duration after trial ends
    private static final int PROD_GRACE_PERIOD_DAYS = 7;

    @PostConstruct
    public void init() {
        debugConfig.logConfiguration();
    }
    
    /**
     * Get the 3-day reminder threshold (uses debug config if enabled)
     */
    private int getReminder3DaysThreshold() {
        return debugConfig.isEnabled() 
            ? debugConfig.getEffectiveReminder3DaysThreshold() 
            : PROD_REMINDER_3_DAYS;
    }
    
    /**
     * Get the 1-day reminder threshold (uses debug config if enabled)
     */
    private int getReminder1DayThreshold() {
        return debugConfig.isEnabled() 
            ? debugConfig.getEffectiveReminder1DayThreshold() 
            : PROD_REMINDER_1_DAY;
    }
    
    /**
     * Get the grace period duration (uses debug config if enabled)
     */
    private int getGracePeriodDays() {
        return debugConfig.isEnabled() 
            ? (int) debugConfig.getEffectiveGracePeriodDuration().toDays()
            : PROD_GRACE_PERIOD_DAYS;
    }

    /**
     * Scheduled task: Run daily at 2 AM to check trial expirations
     * In debug mode, use a fixed delay instead for more frequent checks
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void processTrialExpirations() {
        log.info("Starting trial expiration check...");
        
        // Check for trials ending soon (first reminder)
        sendReminderNotifications(getReminder3DaysThreshold());
        
        // Check for trials ending very soon (final reminder)
        sendReminderNotifications(getReminder1DayThreshold());
        
        // Process expired trials
        processExpiredTrials();
        
        // Process ended grace periods
        processEndedGracePeriods();
        
        log.info("Trial expiration check completed");
    }
    
    /**
     * Debug scheduled task: Run frequently when debug mode is enabled
     * This runs every minute in debug mode for rapid testing
     */
    @Scheduled(fixedDelayString = "${trial.debug.check-interval:24h}", initialDelay = 60000)
    @Transactional
    public void processTrialExpirationsDebug() {
        if (debugConfig.isEnabled()) {
            log.debug("Running debug trial expiration check...");
            processTrialExpirations();
        }
    }

    /**
     * Send reminder emails to trials ending in specified days.
     * In debug mode, uses minutes instead of days for rapid testing.
     */
    @Transactional(readOnly = true)
    public void sendReminderNotifications(int daysBeforeExpiration) {
        // In debug mode, we check for trials ending in minutes
        LocalDateTime targetDate;
        LocalDateTime nextWindow;
        
        if (debugConfig.isEnabled()) {
            // Debug: check in minutes
            targetDate = LocalDateTime.now().plusMinutes(daysBeforeExpiration);
            nextWindow = targetDate.plusMinutes(1);
        } else {
            // Production: check in days
            targetDate = LocalDateTime.now().plusDays(daysBeforeExpiration);
            nextWindow = targetDate.plusDays(1);
        }
        
        List<Tenant> trialsEndingSoon = tenantRepository.findBySubscriptionStatusAndTrialEndsAtBetween(
            SubscriptionStatus.TRIAL, targetDate, nextWindow);
        
        for (Tenant tenant : trialsEndingSoon) {
            // Skip if already sent reminder for this threshold (only for 3-day/production threshold)
            int prod3DayThreshold = PROD_REMINDER_3_DAYS;
            if (!debugConfig.isEnabled() && daysBeforeExpiration == prod3DayThreshold && tenant.isTrialReminderSent()) {
                continue;
            }
            
            try {
                sendTrialEndingEmail(tenant, daysBeforeExpiration);
                
                // Mark reminder as sent (only for first reminder)
                if (!debugConfig.isEnabled() && daysBeforeExpiration == prod3DayThreshold) {
                    tenant.setTrialReminderSent(true);
                    tenantRepository.save(tenant);
                }
                
                log.info("Sent {}-day reminder to tenant: {}", daysBeforeExpiration, tenant.getCompanyName());
            } catch (Exception e) {
                log.error("Failed to send reminder to {}: {}", tenant.getAdminEmail(), e.getMessage());
            }
        }
    }

    /**
     * Process trials that have expired.
     * - Send expiration notification
     * - Start grace period
     * - Update status to EXPIRED
     */
    @Transactional
    public void processExpiredTrials() {
        LocalDateTime now = LocalDateTime.now();
        
        // Find trials that just expired (ended in last 24 hours)
        LocalDateTime yesterday = now.minusDays(1);
        List<Tenant> expiredTrials = tenantRepository.findBySubscriptionStatusAndTrialEndsAtBetween(
            SubscriptionStatus.TRIAL, yesterday, now);
        
        for (Tenant tenant : expiredTrials) {
            try {
                // Send trial ended notification
                if (!tenant.isTrialEndedNotificationSent()) {
                    sendTrialEndedEmail(tenant);
                    tenant.setTrialEndedNotificationSent(true);
                }
                
                // Start grace period (7 days in production, configurable in debug)
                LocalDateTime gracePeriodEnds;
                if (debugConfig.isEnabled()) {
                    gracePeriodEnds = now.plus(debugConfig.getEffectiveGracePeriodDuration());
                } else {
                    gracePeriodEnds = now.plusDays(PROD_GRACE_PERIOD_DAYS);
                }
                tenant.setGracePeriodEndsAt(gracePeriodEnds);
                tenant.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
                tenant.setCurrentPeriodEnd(gracePeriodEnds);
                
                tenantRepository.save(tenant);
                
                log.info("Trial expired for tenant: {}. Grace period until: {}", 
                    tenant.getCompanyName(), gracePeriodEnds);
                    
            } catch (Exception e) {
                log.error("Failed to process expired trial for {}: {}", 
                    tenant.getCompanyName(), e.getMessage());
            }
        }
    }

    /**
     * Process grace periods that have ended.
     * - Send grace period ended notification
     * - Apply soft lock (read-only access)
     */
    @Transactional
    public void processEndedGracePeriods() {
        LocalDateTime now = LocalDateTime.now();
        
        // Find tenants whose grace period ended in last 24 hours
        LocalDateTime yesterday = now.minusDays(1);
        List<Tenant> endedGracePeriods = tenantRepository.findByGracePeriodEndsAtBetween(
            yesterday, now);
        
        for (Tenant tenant : endedGracePeriods) {
            // Only process if not already soft locked
            if (tenant.getSubscriptionStatus() != SubscriptionStatus.SUSPENDED) {
                try {
                    if (!tenant.isGracePeriodNotificationSent()) {
                        sendGracePeriodEndedEmail(tenant);
                        tenant.setGracePeriodNotificationSent(true);
                        tenantRepository.save(tenant);
                    }
                    
                    log.info("Grace period ended for tenant: {}. Soft lock applied.", 
                        tenant.getCompanyName());
                        
                } catch (Exception e) {
                    log.error("Failed to process ended grace period for {}: {}", 
                        tenant.getCompanyName(), e.getMessage());
                }
            }
        }
    }

    /**
     * Send trial ending reminder email.
     */
    private void sendTrialEndingEmail(Tenant tenant, int daysRemaining) {
        if (daysRemaining == 1) {
            emailService.sendTrialEndingSoonEmail(
                tenant.getAdminEmail(),
                tenant.getSchemaName(),
                tenant.getCompanyName()
            );
        } else {
            emailService.sendTrialReminderEmail(
                tenant.getAdminEmail(),
                tenant.getSchemaName(),
                tenant.getCompanyName(),
                daysRemaining
            );
        }
    }

    /**
     * Send trial ended email with grace period info.
     */
    private void sendTrialEndedEmail(Tenant tenant) {
        emailService.sendTrialEndedEmail(
            tenant.getAdminEmail(),
            tenant.getSchemaName(),
            tenant.getCompanyName()
        );
    }

    /**
     * Send grace period ended email.
     */
    private void sendGracePeriodEndedEmail(Tenant tenant) {
        emailService.sendGracePeriodEndedEmail(
            tenant.getAdminEmail(),
            tenant.getSchemaName(),
            tenant.getCompanyName()
        );
    }

}
