package com.possapp.backend.config;

import com.possapp.backend.service.TenantService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantFilterConfig {

    @Bean
    public TenantFilter tenantFilter(TenantService tenantService) {
        return new TenantFilter(tenantService);
    }

    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter tenantFilter) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(tenantFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Integer.MIN_VALUE); // First filter
        registration.setName("tenantFilter");
        return registration;
    }
}
