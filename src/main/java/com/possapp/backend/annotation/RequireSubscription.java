package com.possapp.backend.annotation;

import com.possapp.backend.entity.SubscriptionPlan;

import java.lang.annotation.*;

/**
 * ============================================================================
 * REQUIRE SUBSCRIPTION ANNOTATION
 * ============================================================================
 * Annotates a controller method that requires a minimum subscription plan.
 * Throws an exception if the tenant is on a lower plan.
 * 
 * Example usage:
 * @RequireSubscription(SubscriptionPlan.BUSINESS)
 * @GetMapping("/reports")
 * public ResponseEntity<ApiResponse<Report>> getReports() { ... }
 * ============================================================================
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireSubscription {
    
    /**
     * Minimum subscription plan required.
     */
    SubscriptionPlan value();
    
    /**
     * Error message to return if requirement is not met.
     * If empty, a default message will be used.
     */
    String message() default "";
}
