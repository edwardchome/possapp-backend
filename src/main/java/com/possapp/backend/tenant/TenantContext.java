package com.possapp.backend.tenant;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {
    
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final String DEFAULT_TENANT = "public";
    
    public static void setCurrentTenant(String tenantId) {
        log.debug("Setting current tenant to: {}", tenantId);
        CURRENT_TENANT.set(tenantId);
    }
    
    public static String getCurrentTenant() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }
    
    public static void clear() {
        log.debug("Clearing tenant context");
        CURRENT_TENANT.remove();
    }
    
    public static boolean isDefaultTenant() {
        return DEFAULT_TENANT.equals(getCurrentTenant());
    }
}
