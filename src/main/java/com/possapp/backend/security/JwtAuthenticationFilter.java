package com.possapp.backend.security;

import com.possapp.backend.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                     FilterChain filterChain) throws ServletException, IOException {
        String currentTenant = TenantContext.getCurrentTenant();
        String requestUri = request.getRequestURI();
        
        log.info("[JWT FILTER] Request: {} | TenantContext: {}", requestUri, currentTenant);
        
        try {
            String jwt = getJwtFromRequest(request);
            log.debug("JWT token present: {}", StringUtils.hasText(jwt));
            
            if (StringUtils.hasText(jwt)) {
                // Validate token and tenant match
                if (!jwtTokenProvider.validateToken(jwt)) {
                    log.warn("JWT token is invalid or expired");
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // Get tenant from token and validate against request tenant
                String tokenTenantId = jwtTokenProvider.getTenantIdFromToken(jwt);
                
                if (tokenTenantId == null) {
                    log.warn("JWT token missing tenantId claim - rejecting request");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Token missing tenant information\"}");
                    return;
                }
                
                log.info("[JWT FILTER] Comparing - Token tenant: '{}' | Context tenant: '{}'", tokenTenantId, currentTenant);
                
                if (currentTenant == null || !currentTenant.equals(tokenTenantId)) {
                    log.error("[JWT FILTER] TENANT MISMATCH: Token tenant='{}' | Context tenant='{}'", tokenTenantId, currentTenant);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Cross-tenant access denied\"}");
                    return;
                }
                
                String username = jwtTokenProvider.getUsernameFromToken(jwt);
                log.debug("JWT valid for user: {} in tenant: {}", username, tokenTenantId);
                
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                log.debug("UserDetails loaded: {}", userDetails.getUsername());
                
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authentication set in security context");
            } else {
                log.debug("No JWT token in request");
            }
        } catch (Exception ex) {
            log.error("JWT FILTER EXCEPTION: {}", ex.getMessage(), ex);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
