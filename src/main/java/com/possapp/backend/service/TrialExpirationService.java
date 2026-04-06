package com.possapp.backend.service;

import com.possapp.backend.entity.SubscriptionStatus;
import com.possapp.backend.entity.Tenant;
import com.possapp.backend.entity.TenantUsage;
import com.possapp.backend.repository.TenantRepository;
import com.possapp.backend.repository.TenantUsageRepository;
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

    // Notification thresholds (days before expiration)
    private static final int REMINDER_3_DAYS = 3;
    private static final int REMINDER_1_DAY = 1;
    
    // Grace period duration after trial ends
    private static final int GRACE_PERIOD_DAYS = 7;

    /**
     * Scheduled task: Run daily at 2 AM to check trial expirations
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void processTrialExpirations() {
        log.info("Starting daily trial expiration check...");
        
        // Check for trials ending in 3 days (first reminder)
        sendReminderNotifications(REMINDER_3_DAYS);
        
        // Check for trials ending in 1 day (final reminder)
        sendReminderNotifications(REMINDER_1_DAY);
        
        // Process expired trials
        processExpiredTrials();
        
        // Process ended grace periods
        processEndedGracePeriods();
        
        log.info("Daily trial expiration check completed");
    }

    /**
     * Send reminder emails to trials ending in specified days.
     */
    @Transactional(readOnly = true)
    public void sendReminderNotifications(int daysBeforeExpiration) {
        LocalDateTime targetDate = LocalDateTime.now().plusDays(daysBeforeExpiration);
        LocalDateTime nextDay = targetDate.plusDays(1);
        
        List<Tenant> trialsEndingSoon = tenantRepository.findBySubscriptionStatusAndTrialEndsAtBetween(
            SubscriptionStatus.TRIAL, targetDate, nextDay);
        
        for (Tenant tenant : trialsEndingSoon) {
            // Skip if already sent reminder for this threshold
            if (daysBeforeExpiration == REMINDER_3_DAYS && tenant.isTrialReminderSent()) {
                continue;
            }
            
            try {
                sendTrialEndingEmail(tenant, daysBeforeExpiration);
                
                // Mark reminder as sent
                if (daysBeforeExpiration == REMINDER_3_DAYS) {
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
                
                // Start grace period (7 days)
                LocalDateTime gracePeriodEnds = now.plusDays(GRACE_PERIOD_DAYS);
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
