package com.possapp.backend.config;

import com.possapp.backend.entity.Tenant;
import com.possapp.backend.service.TenantService;
import com.possapp.backend.tenant.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextListener;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class TenantFilter implements Filter {
    
    private static final String TENANT_HEADER = "X-Tenant-ID";
    
    // Paths that are completely public and don't need tenant context
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
        "/api/v1/tenants/register",
        "/api/v1/auth/register/initiate",
        "/api/v1/auth/register/complete",
        "/api/v1/auth/verify-email",
        "/api/v1/auth/resend-verification",
        "/api/v1/auth/registration-status",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
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
        String method = httpRequest.getMethod();
        
        // Allow CORS preflight (OPTIONS) requests to pass through
        if ("OPTIONS".equalsIgnoreCase(method)) {
            log.debug("Allowing CORS preflight request: {} {}", method, requestUri);
            chain.doFilter(request, response);
            return;
        }
        
        // Skip tenant validation for completely public paths
        if (isPublicPath(requestUri)) {
            chain.doFilter(request, response);
            return;
        }
        
        String tenantId = httpRequest.getHeader(TENANT_HEADER);
        
        // Debug: Log all headers
        if (log.isDebugEnabled()) {
            log.debug("Request: {} {}", method, requestUri);
            log.debug("Tenant header value: {}", tenantId);
            java.util.Enumeration<String> headerNames = httpRequest.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                log.debug("Header: {} = {}", headerName, httpRequest.getHeader(headerName));
            }
        }
        
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Missing tenant header for request: {} {}", method, requestUri);
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.setContentType("application/json");
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
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\":\"Tenant is not active\"}");
                return;
            }
            
            // Now set the actual tenant context for this request
            TenantContext.setCurrentTenant(tenantId);
            log.info("Tenant context set to: {} for request: {} {}", tenantId, method, requestUri);
            
            try {
                chain.doFilter(request, response);
            } finally {
                // Clear tenant context after the ENTIRE request is complete
                // This ensures Spring Security authentication can access the tenant
                log.debug("Clearing tenant context for request: {}", requestUri);
                TenantContext.clear();
            }
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant: {}", tenantId);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Invalid tenant\"}");
            TenantContext.clear();
        }
    }
    
    private boolean isPublicPath(String requestUri) {
        return PUBLIC_PATHS.stream().anyMatch(path -> requestUri.startsWith(path));
    }
}
