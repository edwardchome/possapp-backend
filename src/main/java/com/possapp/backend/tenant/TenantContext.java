package com.possapp.backend.tenant;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {
    
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    public static final String DEFAULT_TENANT = "public";
    
    public static void setCurrentTenant(String tenantId) {
        log.info("TENANT CONTEXT SET: {} (previous: {})", tenantId, CURRENT_TENANT.get());
        CURRENT_TENANT.set(tenantId);
    }
    
    /**
     * Get current tenant identifier.
     * Returns null if not set - Hibernate will handle the default.
     */
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }
    
    /**
     * Get current tenant identifier with fallback to default.
     * Use this when you need a non-null value.
     */
    public static String getCurrentTenantOrDefault() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }
    
    public static void clear() {
        log.info("TENANT CONTEXT CLEARED (was: {})", CURRENT_TENANT.get());
        CURRENT_TENANT.remove();
    }
    
    public static boolean isDefaultTenant() {
        return DEFAULT_TENANT.equals(getCurrentTenantOrDefault());
    }
    
    public static boolean hasTenantSet() {
        return CURRENT_TENANT.get() != null;
    }
}
