package com.possapp.backend.aspect;

import com.possapp.backend.entity.SubscriptionStatus;
import com.possapp.backend.entity.Tenant;
import com.possapp.backend.exception.SoftLockException;
import com.possapp.backend.repository.TenantRepository;
import com.possapp.backend.tenant.TenantContext;
import com.possapp.backend.repository.TenantRepository;
import com.possapp.backend.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * ============================================================================
 * SOFT LOCK ASPECT
 * ============================================================================
 * Enforces soft lock restrictions when a tenant's subscription has expired
 * and the grace period has ended.
 * 
 * SOFT LOCK BEHAVIOR:
 * - READ operations: ALLOWED (viewing data, reports, history)
 * - WRITE operations: BLOCKED (sales, adding products, users, inventory)
 * 
 * This allows users to access their data for export/migration while
 * preventing new business operations until payment is made.
 * ============================================================================
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SoftLockAspect {

    private final TenantRepository tenantRepository;
    
    // HTTP methods that are considered read operations
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    
    // Endpoints that are always allowed (even in soft lock)
    private static final Set<String> ALLOWED_ENDPOINTS = Set.of(
        "/api/v1/auth",
        "/api/v1/tenants/subscription",
        "/api/v1/subscription/plans",
        "/api/v1/store/config",
        "/actuator"
    );
    
    // Endpoints that should be blocked during soft lock (write operations)
    private static final Set<String> BLOCKED_WRITE_ENDPOINTS = Set.of(
        "/api/v1/sales",              // Creating sales
        "/api/v1/products",           // Creating/editing products
        "/api/v1/inventory",          // Inventory transactions
        "/api/v1/users",              // Adding users
        "/api/v1/branches",           // Adding branches
        "/api/v1/categories",         // Managing categories
        "/api/v1/units",              // Managing units
        "/api/v1/receipts",           // Voiding receipts
        "/api/v1/tenants/subscription/upgrade",  // Even upgrades need special handling
        "/api/v1/tenants/subscription/cancel"
    );

    /**
     * Check soft lock before any controller method execution.
     * This is the main enforcement point.
     */
    @Before("execution(* com.possapp.backend.controller..*(..))")
    public void checkSoftLock(JoinPoint joinPoint) {
        String schemaName = TenantContext.getCurrentTenant();
        if (schemaName == null) {
            return; // No tenant context, public endpoint
        }
        
        // Get current HTTP request
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return;
        }
        
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        
        // Skip check for allowed endpoints
        if (isAllowedEndpoint(requestUri)) {
            return;
        }
        
        // Check if this is a read operation
        if (isReadOperation(method, requestUri)) {
            return; // Read operations are always allowed
        }
        
        // Check tenant soft lock status
        Tenant tenant = tenantRepository.findBySchemaName(schemaName).orElse(null);
        if (tenant == null) {
            return;
        }
        
        // Check if tenant is soft locked
        if (isSoftLocked(tenant)) {
            log.warn("Soft lock enforced for tenant: {}. Blocked {} {}", 
                schemaName, method, requestUri);
            throw new SoftLockException(
                "Your subscription has expired and your account is now in read-only mode. " +
                "You can view your data but cannot create new sales, products, or users. " +
                "Please upgrade to restore full access.",
                tenant.getSubscriptionPlan().name(),
                getDaysSinceSoftLock(tenant)
            );
        }
    }
    
    /**
     * Check if the tenant is in soft lock mode.
     */
    private boolean isSoftLocked(Tenant tenant) {
        // Hard suspension - no access at all
        if (tenant.getSubscriptionStatus() == SubscriptionStatus.SUSPENDED) {
            return true;
        }
        
        // Active or in trial - full access
        if (tenant.getSubscriptionStatus() == SubscriptionStatus.ACTIVE || 
            tenant.getSubscriptionStatus() == SubscriptionStatus.TRIAL) {
            return false;
        }
        
        // In grace period - full access
        if (tenant.isInGracePeriod()) {
            return false;
        }
        
        // Expired, Cancelled, or Past Due without grace period - soft lock
        if (tenant.getSubscriptionStatus() == SubscriptionStatus.EXPIRED ||
            tenant.getSubscriptionStatus() == SubscriptionStatus.CANCELLED ||
            tenant.getSubscriptionStatus() == SubscriptionStatus.PAST_DUE) {
            
            // Check if grace period has ended
            if (tenant.getGracePeriodEndsAt() == null) {
                return true; // No grace period defined - soft lock
            }
            
            return tenant.getGracePeriodEndsAt().isBefore(LocalDateTime.now());
        }
        
        // Trial ended without subscription
        if (!tenant.isInTrial() && tenant.getTrialEndsAt() != null) {
            // Trial ended
            if (tenant.getTrialEndsAt().isBefore(LocalDateTime.now())) {
                // Check grace period
                if (tenant.getGracePeriodEndsAt() == null) {
                    return true;
                }
                return tenant.getGracePeriodEndsAt().isBefore(LocalDateTime.now());
            }
        }
        
        return false;
    }
    
    /**
     * Check if this is a read operation that should be allowed.
     */
    private boolean isReadOperation(String httpMethod, String requestUri) {
        // GET requests are generally read operations
        if ("GET".equals(httpMethod)) {
            // But some GET endpoints might trigger writes or be restricted
            return true;
        }
        
        // Check if this is a blocked write endpoint
        for (String blockedPrefix : BLOCKED_WRITE_ENDPOINTS) {
            if (requestUri.startsWith(blockedPrefix)) {
                // It's a blocked endpoint - check if it's a write method
                return READ_METHODS.contains(httpMethod);
            }
        }
        
        // Default: allow read methods, block others
        return READ_METHODS.contains(httpMethod);
    }
    
    /**
     * Check if the endpoint is always allowed (even in soft lock).
     */
    private boolean isAllowedEndpoint(String requestUri) {
        for (String allowedPrefix : ALLOWED_ENDPOINTS) {
            if (requestUri.startsWith(allowedPrefix)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the current HTTP request.
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            return attrs.getRequest();
        }
        return null;
    }
    
    /**
     * Get days since soft lock started.
     */
    private int getDaysSinceSoftLock(Tenant tenant) {
        if (tenant.getGracePeriodEndsAt() == null) {
            // Grace period never started or not defined
            if (tenant.getTrialEndsAt() != null && tenant.getTrialEndsAt().isBefore(LocalDateTime.now())) {
                return (int) ChronoUnit.DAYS.between(tenant.getTrialEndsAt(), LocalDateTime.now());
            }
            return 0;
        }
        // Days since grace period ended
        return (int) ChronoUnit.DAYS.between(tenant.getGracePeriodEndsAt(), LocalDateTime.now());
    }
}
