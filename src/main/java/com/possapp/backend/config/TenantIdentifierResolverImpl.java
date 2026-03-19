package com.possapp.backend.config;

import com.possapp.backend.tenant.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver<String> {
    
    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getCurrentTenant();
    }
    
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
