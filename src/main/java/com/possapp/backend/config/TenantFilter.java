package com.possapp.backend.config;

import com.possapp.backend.entity.Tenant;
import com.possapp.backend.service.TenantService;
import com.possapp.backend.tenant.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@Order(1)
public class TenantFilter implements Filter {
    
    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
        "/api/v1/auth/register",
        "/api/v1/auth/login",
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
        
        // Skip tenant validation for public paths
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
            TenantContext.setCurrentTenant("public");
            Tenant tenant = tenantService.findBySchemaName(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid tenant: " + tenantId));
            
            if (!tenant.isActive()) {
                log.warn("Tenant {} is not active", tenantId);
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.getWriter().write("{\"error\":\"Tenant is not active\"}");
                return;
            }
            
            // Set the tenant context for this request
            TenantContext.setCurrentTenant(tenantId);
            
            log.debug("Set tenant context to: {} for request: {}", tenantId, requestUri);
            chain.doFilter(request, response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant: {}", tenantId);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"error\":\"Invalid tenant\"}");
        } finally {
            TenantContext.clear();
        }
    }
    
    private boolean isPublicPath(String requestUri) {
        return PUBLIC_PATHS.stream().anyMatch(path -> requestUri.startsWith(path));
    }
}
