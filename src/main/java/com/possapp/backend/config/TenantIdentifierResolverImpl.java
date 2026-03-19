package com.possapp.backend.config;

import com.possapp.backend.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver<String> {
    
    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("No tenant context set, using default schema");
            return TenantContext.DEFAULT_TENANT;
        }
        log.debug("Resolved tenant: {}", tenantId);
        return tenantId;
    }
    
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
