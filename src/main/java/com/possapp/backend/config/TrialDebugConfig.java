package com.possapp.backend.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * ============================================================================
 * TRIAL DEBUG CONFIGURATION
 * ============================================================================
 * 
 * This configuration class allows shortening trial notification periods
 * for debugging and testing purposes.
 * 
 * WARNING: Do not commit changes with debug mode enabled in production!
 * 
 * To enable debug mode, add to application.yml:
 *   trial:
 *     debug:
 *       enabled: true
 * 
 * ============================================================================
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "trial.debug")
public class TrialDebugConfig {

    /**
     * Set to true to enable debug mode with shortened intervals
     * Default: false (use production intervals)
     */
    private boolean enabled = false;

    // ============================================================================
    // DEBUG INTERVALS (used when enabled = true)
    // ============================================================================
    
    /**
     * How often to check for trial expirations
     * Production: 24 hours (daily at 2 AM)
     * Debug: 5 minutes
     */
    private Duration checkInterval = Duration.ofMinutes(5);

    /**
     * Days before expiration to send first reminder
     * Production: 3 days
     * Debug: 1 minute (for immediate testing)
     */
    private int reminder3DaysThreshold = 1; // in minutes when in debug mode

    /**
     * Days before expiration to send final reminder  
     * Production: 1 day
     * Debug: 30 seconds (for immediate testing)
     */
    private int reminder1DayThreshold = 30; // in seconds when in debug mode

    /**
     * Grace period duration
     * Production: 7 days
     * Debug: 2 minutes
     */
    private Duration gracePeriodDuration = Duration.ofMinutes(2);

    /**
     * Trial duration
     * Production: 14 days
     * Debug: 5 minutes
     */
    private Duration trialDuration = Duration.ofMinutes(5);

    // ============================================================================
    // PRODUCTION DEFAULTS (used when enabled = false)
    // ============================================================================
    
    private static final Duration PROD_CHECK_INTERVAL = Duration.ofHours(24);
    private static final int PROD_REMINDER_3_DAYS = 3;
    private static final int PROD_REMINDER_1_DAY = 1;
    private static final Duration PROD_GRACE_PERIOD = Duration.ofDays(7);
    private static final Duration PROD_TRIAL_DURATION = Duration.ofDays(14);

    // ============================================================================
    // GETTERS THAT RETURN APPROPRIATE VALUES BASED ON DEBUG MODE
    // ============================================================================

    public Duration getEffectiveCheckInterval() {
        return enabled ? checkInterval : PROD_CHECK_INTERVAL;
    }

    public int getEffectiveReminder3DaysThreshold() {
        return enabled ? reminder3DaysThreshold : PROD_REMINDER_3_DAYS;
    }

    public int getEffectiveReminder1DayThreshold() {
        return enabled ? reminder1DayThreshold : PROD_REMINDER_1_DAY;
    }

    public Duration getEffectiveGracePeriodDuration() {
        return enabled ? gracePeriodDuration : PROD_GRACE_PERIOD;
    }

    public Duration getEffectiveTrialDuration() {
        return enabled ? trialDuration : PROD_TRIAL_DURATION;
    }

    /**
     * Log current configuration on startup
     */
    public void logConfiguration() {
        log.info("========================================");
        log.info("Trial Debug Configuration:");
        log.info("  Debug Mode: {}", enabled ? "ENABLED" : "DISABLED");
        log.info("  Check Interval: {}", getEffectiveCheckInterval());
        log.info("  3-Day Reminder Threshold: {}", getEffectiveReminder3DaysThreshold());
        log.info("  1-Day Reminder Threshold: {}", getEffectiveReminder1DayThreshold());
        log.info("  Grace Period Duration: {}", getEffectiveGracePeriodDuration());
        log.info("  Trial Duration: {}", getEffectiveTrialDuration());
        log.info("========================================");
        
        if (enabled) {
            log.warn("⚠️  TRIAL DEBUG MODE IS ENABLED!");
            log.warn("⚠️  Notification intervals are shortened for testing.");
            log.warn("⚠️  DO NOT USE IN PRODUCTION!");
        }
    }
}
