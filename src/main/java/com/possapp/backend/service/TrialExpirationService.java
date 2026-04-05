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
        String subject = daysRemaining == 1 
            ? "Your PossApp Trial Ends Tomorrow - Action Required"
            : "Your PossApp Trial Ends in " + daysRemaining + " Days";
            
        String body = buildTrialEndingEmailBody(tenant, daysRemaining);
        
        emailService.sendHtmlEmail(tenant.getAdminEmail(), subject, body);
    }

    /**
     * Send trial ended email with grace period info.
     */
    private void sendTrialEndedEmail(Tenant tenant) {
        String subject = "Your PossApp Trial Has Ended - Grace Period Started";
        String body = buildTrialEndedEmailBody(tenant);
        
        emailService.sendHtmlEmail(tenant.getAdminEmail(), subject, body);
    }

    /**
     * Send grace period ended email.
     */
    private void sendGracePeriodEndedEmail(Tenant tenant) {
        String subject = "Your PossApp Account is Now in Read-Only Mode";
        String body = buildGracePeriodEndedEmailBody(tenant);
        
        emailService.sendHtmlEmail(tenant.getAdminEmail(), subject, body);
    }

    /**
     * Build trial ending reminder email body.
     */
    private String buildTrialEndingEmailBody(Tenant tenant, int daysRemaining) {
        String urgencyColor = daysRemaining == 1 ? "#dc2626" : "#ea580c";
        String urgencyText = daysRemaining == 1 
            ? "<strong style=\"color: #dc2626;\">Your trial ends TOMORROW!</strong>" 
            : "Your trial ends in <strong>" + daysRemaining + " days</strong>.";
            
        return String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <meta charset=\"UTF-8\">" +
            "    <style>" +
            "        body { font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }" +
            "        .container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 10px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
            "        .header { text-align: center; margin-bottom: 30px; }" +
            "        .header h1 { color: %s; margin: 0; }" +
            "        .content { margin-bottom: 30px; }" +
            "        .button { display: inline-block; background-color: #4a6cf7; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; margin: 20px 0; }" +
            "        .info-box { background-color: #f0f9ff; border-left: 4px solid #4a6cf7; padding: 15px; margin: 15px 0; }" +
            "        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; }" +
            "    </style>" +
            "</head>" +
            "<body>" +
            "    <div class=\"container\">" +
            "        <div class=\"header\">" +
            "            <h1>Trial Ending Soon</h1>" +
            "        </div>" +
            "        <div class=\"content\">" +
            "            <p>Hi %s,</p>" +
            "            <p>%s</p>" +
            "            <div class=\"info-box\">" +
            "                <strong>What happens next?</strong><br>" +
            "                • After your trial ends, you'll enter a 7-day grace period<br>" +
            "                • During the grace period, all features remain active<br>" +
            "                • After the grace period, your account becomes read-only<br>" +
            "                • Upgrade anytime to continue using all features" +
            "            </div>" +
            "            <center><a href=\"%s\" class=\"button\">Upgrade Now</a></center>" +
            "            <p>Questions? Reply to this email or contact support@possapp.com</p>" +
            "        </div>" +
            "        <div class=\"footer\">" +
            "            <p>&copy; 2025 PossApp. All rights reserved.</p>" +
            "        </div>" +
            "    </div>" +
            "</body>" +
            "</html>",
            urgencyColor, tenant.getCompanyName(), urgencyText, buildUpgradeLink(tenant)
        );
    }

    /**
     * Build trial ended email body.
     */
    private String buildTrialEndedEmailBody(Tenant tenant) {
        return String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <meta charset=\"UTF-8\">" +
            "    <style>" +
            "        body { font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }" +
            "        .container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 10px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
            "        .header { text-align: center; margin-bottom: 30px; }" +
            "        .header h1 { color: #ea580c; margin: 0; }" +
            "        .content { margin-bottom: 30px; }" +
            "        .button { display: inline-block; background-color: #4a6cf7; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; margin: 20px 0; }" +
            "        .info-box { background-color: #fff7ed; border-left: 4px solid #ea580c; padding: 15px; margin: 15px 0; }" +
            "        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; }" +
            "    </style>" +
            "</head>" +
            "<body>" +
            "    <div class=\"container\">" +
            "        <div class=\"header\">" +
            "            <h1>Your Trial Has Ended</h1>" +
            "        </div>" +
            "        <div class=\"content\">" +
            "            <p>Hi %s,</p>" +
            "            <p>Your PossApp trial period has ended. <strong>Good news:</strong> We've started a 7-day grace period so you can continue using all features while you decide.</p>" +
            "            <div class=\"info-box\">" +
            "                <strong>Grace Period Details:</strong><br>" +
            "                • Duration: 7 days<br>" +
            "                • All features remain active<br>" +
            "                • No data will be lost<br>" +
            "                • Grace period ends: %s" +
            "            </div>" +
            "            <center><a href=\"%s\" class=\"button\">Upgrade Now</a></center>" +
            "            <p>Questions? Reply to this email or contact support@possapp.com</p>" +
            "        </div>" +
            "        <div class=\"footer\">" +
            "            <p>&copy; 2025 PossApp. All rights reserved.</p>" +
            "        </div>" +
            "    </div>" +
            "</body>" +
            "</html>",
            tenant.getCompanyName(), 
            tenant.getGracePeriodEndsAt() != null ? tenant.getGracePeriodEndsAt().toLocalDate() : "Soon",
            buildUpgradeLink(tenant)
        );
    }

    /**
     * Build grace period ended email body.
     */
    private String buildGracePeriodEndedEmailBody(Tenant tenant) {
        return String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <meta charset=\"UTF-8\">" +
            "    <style>" +
            "        body { font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }" +
            "        .container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 10px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
            "        .header { text-align: center; margin-bottom: 30px; }" +
            "        .header h1 { color: #dc2626; margin: 0; }" +
            "        .content { margin-bottom: 30px; }" +
            "        .button { display: inline-block; background-color: #16a34a; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; margin: 20px 0; }" +
            "        .info-box { background-color: #fef2f2; border-left: 4px solid #dc2626; padding: 15px; margin: 15px 0; }" +
            "        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; }" +
            "    </style>" +
            "</head>" +
            "<body>" +
            "    <div class=\"container\">" +
            "        <div class=\"header\">" +
            "            <h1>Account in Read-Only Mode</h1>" +
            "        </div>" +
            "        <div class=\"content\">" +
            "            <p>Hi %s,</p>" +
            "            <p>Your grace period has ended. Your PossApp account is now in <strong>read-only mode</strong>.</p>" +
            "            <div class=\"info-box\">" +
            "                <strong>What You Can Still Do:</strong><br>" +
            "                ✅ View all your data<br>" +
            "                ✅ View sales history and reports<br>" +
            "                ✅ Export your data<br><br>" +
            "                <strong>What's Restricted:</strong><br>" +
            "                ❌ Create new sales<br>" +
            "                ❌ Add/edit products<br>" +
            "                ❌ Add users or branches<br>" +
            "                ❌ Modify inventory" +
            "            </div>" +
            "            <p><strong>Your data is safe!</strong> Upgrade anytime to restore full access.</p>" +
            "            <center><a href=\"%s\" class=\"button\">Upgrade to Restore Access</a></center>" +
            "            <p>Questions? Reply to this email or contact support@possapp.com</p>" +
            "        </div>" +
            "        <div class=\"footer\">" +
            "            <p>&copy; 2025 PossApp. All rights reserved.</p>" +
            "        </div>" +
            "    </div>" +
            "</body>" +
            "</html>",
            tenant.getCompanyName(), buildUpgradeLink(tenant)
        );
    }

    /**
     * Build upgrade link for tenant.
     */
    private String buildUpgradeLink(Tenant tenant) {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        return baseUrl + "/subscription/upgrade?tenant=" + tenant.getSchemaName();
    }
}
