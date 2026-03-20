package com.possapp.backend.config;

import com.possapp.backend.service.TenantService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This configuration is now DISABLED.
 * TenantFilter is now registered via SecurityConfig as part of Spring Security filter chain.
 * 
 * Keeping this file for reference but beans are commented out.
 */
@Configuration
public class TenantFilterConfig {

    /*
    @Bean
    public TenantFilter tenantFilter(TenantService tenantService) {
        return new TenantFilter(tenantService);
    }

    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter tenantFilter) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(tenantFilter);
        registration.addUrlPatterns("/api/*");
        // Run after CORS filter (which is typically Ordered.HIGHEST_PRECEDENCE + 100)
        // Using 100 ensures we run after CORS but before other filters
        registration.setOrder(100);
        registration.setName("tenantFilter");
        return registration;
    }
    */
}
