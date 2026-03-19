package com.possapp.backend.config;

import com.possapp.backend.entity.Tenant;
import com.possapp.backend.service.TenantService;
import com.possapp.backend.tenant.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class TenantFilter implements Filter {
    
    private static final String TENANT_HEADER = "X-Tenant-ID";
    
    // Paths that don't require authentication but still need tenant context
    private static final List<String> AUTH_PATHS = Arrays.asList(
        "/api/v1/auth/login",
        "/api/v1/auth/register"
    );
    
    // Paths that are completely public and don't need tenant context
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
        "/api/v1/tenants/register",
        "/actuator/health",
        "/actuator/info",
        "/error",
        "/swagger-ui",
        "/v3/api-docs"
    );
    
    private final TenantService tenantService;
    
    public TenantFilter(TenantService tenantService) {
        this.tenantService = tenantService;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String requestUri = httpRequest.getRequestURI();
        
        // Skip tenant validation for completely public paths (tenant registration, health, etc.)
        if (isPublicPath(requestUri)) {
            chain.doFilter(request, response);
            return;
        }
        
        String tenantId = httpRequest.getHeader(TENANT_HEADER);
        
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Missing tenant header for request: {}", requestUri);
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.getWriter().write("{\"error\":\"Missing X-Tenant-ID header\"}");
            return;
        }
        
        // Validate tenant exists and is active
        try {
            // Temporarily use public schema to query tenant info
            TenantContext.setCurrentTenant(TenantContext.DEFAULT_TENANT);
            
            Tenant tenant = tenantService.findBySchemaName(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid tenant: " + tenantId));
            
            if (!tenant.isActive()) {
                log.warn("Tenant {} is not active", tenantId);
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.getWriter().write("{\"error\":\"Tenant is not active\"}");
                return;
            }
            
            // Now set the actual tenant context for this request
            TenantContext.setCurrentTenant(tenantId);
            log.debug("Tenant context set to: {} for request: {}", tenantId, requestUri);
            
            chain.doFilter(request, response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant: {}", tenantId);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"error\":\"Invalid tenant\"}");
        } finally {
            log.debug("Clearing tenant context for request: {}", requestUri);
            TenantContext.clear();
        }
    }
    
    private boolean isPublicPath(String requestUri) {
        return PUBLIC_PATHS.stream().anyMatch(path -> requestUri.startsWith(path));
    }
}
