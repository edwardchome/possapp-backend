package com.possapp.backend.aspect;

import com.possapp.backend.annotation.EnforceLimit;
import com.possapp.backend.annotation.RequireFeature;
import com.possapp.backend.annotation.RequireSubscription;
import com.possapp.backend.dto.SubscriptionDto;
import com.possapp.backend.entity.Feature;
import com.possapp.backend.entity.LimitType;
import com.possapp.backend.entity.SubscriptionPlan;
import com.possapp.backend.exception.SubscriptionAccessDeniedException;
import com.possapp.backend.exception.SubscriptionLimitExceededException;
import com.possapp.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * SUBSCRIPTION ASPECT
 * ============================================================================
 * Aspect that enforces subscription requirements on controller methods.
 * Handles @RequireSubscription, @RequireFeature, and @EnforceLimit annotations.
 * ============================================================================
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SubscriptionAspect {

    private final SubscriptionService subscriptionService;

    /**
     * Enforce minimum subscription plan requirement.
     * Targets: Any method annotated with @RequireSubscription in controller package
     */
    @Before("@annotation(requireSubscription) && execution(* com.possapp.backend.controller..*(..))")
    public void enforceSubscriptionPlan(RequireSubscription requireSubscription) {
        SubscriptionPlan requiredPlan = requireSubscription.value();
        String customMessage = requireSubscription.message();
        
        log.debug("Checking subscription requirement: minimum plan = {}", requiredPlan);
        
        SubscriptionDto currentSubscription = subscriptionService.getCurrentSubscription();
        SubscriptionPlan currentPlan = SubscriptionPlan.valueOf(currentSubscription.getPlan());
        
        if (!currentPlan.isAtLeast(requiredPlan)) {
            String message = customMessage.isEmpty() 
                ? String.format("This feature requires the %s plan or higher. " +
                               "Your current plan is %s. Please upgrade to access this feature.",
                               requiredPlan.getDisplayName(), currentPlan.getDisplayName())
                : customMessage;
            
            log.warn("Subscription plan requirement not met. Required: {}, Current: {}", 
                    requiredPlan, currentPlan);
            
            throw new SubscriptionAccessDeniedException(message, requiredPlan, currentPlan);
        }
    }

    /**
     * Enforce feature availability requirement.
     * Targets: Any method annotated with @RequireFeature in controller package
     */
    @Before("@annotation(requireFeature) && execution(* com.possapp.backend.controller..*(..))")
    public void enforceFeatureAccess(RequireFeature requireFeature) {
        Feature requiredFeature = requireFeature.value();
        String customMessage = requireFeature.message();
        
        log.debug("Checking feature requirement: {}", requiredFeature);
        
        boolean hasAccess = subscriptionService.hasFeatureAccess(requiredFeature);
        
        if (!hasAccess) {
            SubscriptionDto currentSubscription = subscriptionService.getCurrentSubscription();
            SubscriptionPlan currentPlan = SubscriptionPlan.valueOf(currentSubscription.getPlan());
            
            String message = customMessage.isEmpty()
                ? String.format("The '%s' feature is not available on your %s plan. " +
                               "Please upgrade to access this feature.",
                               requiredFeature.getDisplayName(), currentPlan.getDisplayName())
                : customMessage;
            
            log.warn("Feature access denied. Feature: {}, Current plan: {}", 
                    requiredFeature, currentPlan);
            
            throw new FeatureAccessDeniedException(message, requiredFeature, currentPlan);
        }
    }

    /**
     * Enforce resource limit before creating new resources.
     * Targets: Any method annotated with @EnforceLimit
     */
    @Before("@annotation(enforceLimit)")
    public void enforceResourceLimit(JoinPoint joinPoint, EnforceLimit enforceLimit) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        LimitType limitType = enforceLimit.value();
        
        log.info("========================================");
        log.info("🚨 SUBSCRIPTION ASPECT TRIGGERED");
        log.info("========================================");
        log.info("Method: {}.{}()", className, methodName);
        log.info("Limit Type: {}", limitType);
        log.info("----------------------------------------");
        
        try {
            switch (limitType) {
                case USER -> {
                    log.info("Checking USER limit...");
                    subscriptionService.enforceUserLimit();
                }
                case BRANCH -> {
                    log.info("Checking BRANCH limit...");
                    subscriptionService.enforceBranchLimit();
                }
                case PRODUCT -> {
                    log.info("Checking PRODUCT limit...");
                    subscriptionService.enforceProductLimit();
                }
                default -> throw new IllegalStateException("Unknown limit type: " + limitType);
            }
            log.info("✅ SUBSCRIPTION CHECK PASSED - {} limit OK", limitType);
        } catch (Exception e) {
            log.error("❌ SUBSCRIPTION CHECK FAILED - {} limit exceeded: {}", limitType, e.getMessage());
            throw e;
        }
        log.info("========================================");
    }

    // ==================== CUSTOM EXCEPTIONS ====================

    /**
     * Exception thrown when feature access is denied.
     */
    public static class FeatureAccessDeniedException extends RuntimeException {
        private final Feature feature;
        private final SubscriptionPlan currentPlan;

        public FeatureAccessDeniedException(String message, Feature feature, SubscriptionPlan currentPlan) {
            super(message);
            this.feature = feature;
            this.currentPlan = currentPlan;
        }

        public Feature getFeature() {
            return feature;
        }

        public SubscriptionPlan getCurrentPlan() {
            return currentPlan;
        }

        public String getErrorCode() {
            return "FEATURE_NOT_AVAILABLE_" + feature.name();
        }
    }
}
