# Trial Debug Configuration

This document explains how to use the trial debug configuration for rapid testing of trial expiration workflows.

## Quick Start

To enable debug mode with shortened trial periods:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=trial-debug
```

Or with the seeder:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=seed,trial-debug
```

## What Debug Mode Does

When `trial.debug.enabled=true`, the following intervals are shortened:

| Setting | Production | Debug Mode |
|---------|-----------|------------|
| Check Interval | Daily at 2 AM | Every 1 minute |
| First Reminder | 3 days before | 2 minutes before |
| Final Reminder | 1 day before | 1 minute before |
| Grace Period | 7 days | 2 minutes |
| Trial Duration | 14 days | 5 minutes |

## Configuration Options

Edit `application-trial-debug.yml` to customize:

```yaml
trial:
  debug:
    enabled: true
    check-interval: 1m                    # How often to check
    reminder3-days-threshold: 2           # Minutes before (when debug)
    reminder1-day-threshold: 1            # Minutes before (when debug)
    grace-period-duration: 2m             # Grace period length
    trial-duration: 5m                    # Trial length for new tenants
```

## Testing Workflow

### 1. Start with Debug Profile

```bash
cd backend_api
mvn spring-boot:run -Dspring-boot.run.profiles=seed,trial-debug
```

### 2. Create a Test Tenant

Register a new tenant via the API or mobile app. The trial will last only 5 minutes.

### 3. Watch the Logs

You should see:

```
[TrialExpirationService] Sent 2-minute reminder to tenant: Test Store
[TrialExpirationService] Sent 1-minute reminder to tenant: Test Store
[TrialExpirationService] Trial expired for tenant: Test Store
[TrialExpirationService] Grace period ended for tenant: Test Store
```

### 4. Check Emails

Emails will be sent to the admin email at each stage:
- 3 minutes into trial: First reminder
- 4 minutes into trial: Final reminder  
- 5 minutes: Trial ended, grace period started
- 7 minutes: Grace period ended, soft lock applied

## Disabling Debug Mode

Simply run without the `trial-debug` profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=seed
```

## ⚠️ Important Warnings

1. **DO NOT USE IN PRODUCTION** - Debug mode uses extremely short intervals
2. **Email Rate Limits** - You may hit email provider rate limits with frequent notifications
3. **Database Cleanup** - Debug tenants expire quickly, clean up test data regularly
4. **Scheduled Tasks** - Both the cron and fixed-delay schedulers run in debug mode

## Troubleshooting

### Emails not sending?
- Check `EmailService` is configured with valid SMTP settings
- Check spam folders
- Enable debug logging: `logging.level.com.possapp.backend.service.EmailService: DEBUG`

### Notifications not appearing?
- Check the logs for `[TrialExpirationService]` messages
- Verify `trial.debug.enabled=true` is set
- Check that the tenant has `TRIAL` status and valid `trialEndsAt` date

### Too many notifications?
- Increase the `check-interval` in the config
- Increase the reminder thresholds
