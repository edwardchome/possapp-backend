package com.possapp.backend.annotation;

import com.possapp.backend.entity.Feature;

import java.lang.annotation.*;

/**
 * ============================================================================
 * REQUIRE FEATURE ANNOTATION
 * ============================================================================
 * Annotates a controller method that requires a specific feature to be available.
 * Throws an exception if the feature is not available on the current plan.
 * 
 * Example usage:
 * @RequireFeature(Feature.REPORTS)
 * @GetMapping("/analytics")
 * public ResponseEntity<ApiResponse<Analytics>> getAnalytics() { ... }
 * ============================================================================
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireFeature {
    
    /**
     * Feature required.
     */
    Feature value();
    
    /**
     * Error message to return if feature is not available.
     * If empty, a default message will be used.
     */
    String message() default "";
}
