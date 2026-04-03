package com.possapp.backend.annotation;

import com.possapp.backend.entity.LimitType;

import java.lang.annotation.*;

/**
 * ============================================================================
 * ENFORCE LIMIT ANNOTATION
 * ============================================================================
 * Annotates a controller method that creates a resource subject to subscription limits.
 * Checks if the tenant has reached their limit before allowing the operation.
 * Throws SubscriptionLimitExceededException if limit is reached.
 * 
 * Example usage:
 * @EnforceLimit(LimitType.USER)
 * @PostMapping("/users")
 * public ResponseEntity<ApiResponse<User>> createUser(@RequestBody CreateUserRequest request) { ... }
 * ============================================================================
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnforceLimit {
    
    /**
     * Resource limit type to enforce.
     */
    LimitType value();
    
    /**
     * Error message to return if limit is exceeded.
     * If empty, a default message will be used.
     */
    String message() default "";
}
