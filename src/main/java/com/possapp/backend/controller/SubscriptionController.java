package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.SubscriptionDto;
import com.possapp.backend.dto.SubscriptionPlanDto;
import com.possapp.backend.entity.Feature;
import com.possapp.backend.entity.SubscriptionPlan;
import com.possapp.backend.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================================================
 * SUBSCRIPTION CONTROLLER
 * ============================================================================
 * API endpoints for subscription management:
 * - Get current subscription details
 * - List available plans
 * - Upgrade/downgrade plans
 * - Cancel subscription
 * ============================================================================
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Subscription Management", description = "Subscription plans and management APIs")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * ==========================================================================
     * GET CURRENT SUBSCRIPTION
     * ==========================================================================
     * Returns the current tenant's subscription details including:
     * - Current plan and status
     * - Resource usage vs limits
     * - Available features
     * - Expiry information
     * 
     * ENDPOINT: GET /api/v1/tenants/subscription
     * AUTH: Requires JWT token
     * ==========================================================================
     */
    @GetMapping("/tenants/subscription")
    @Operation(
        summary = "Get current subscription",
        description = "Get the current tenant's subscription details, usage, and features",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<SubscriptionDto>> getCurrentSubscription() {
        log.debug("Getting current subscription");
        SubscriptionDto subscription = subscriptionService.getCurrentSubscription();
        return ResponseEntity.ok(ApiResponse.success(subscription));
    }

    /**
     * ==========================================================================
     * GET SUBSCRIPTION LIMITS/USAGE
     * ==========================================================================
     * Returns just the usage statistics for the current tenant.
     * Useful for quick checks before attempting operations.
     * 
     * ENDPOINT: GET /api/v1/tenants/subscription/limits
     * AUTH: Requires JWT token
     * ==========================================================================
     */
    @GetMapping("/tenants/subscription/limits")
    @Operation(
        summary = "Get subscription usage",
        description = "Get current usage vs limits for the tenant",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<SubscriptionDto>> getSubscriptionLimits() {
        // Returns the same as getCurrentSubscription but emphasizes usage
        SubscriptionDto subscription = subscriptionService.getCurrentSubscription();
        return ResponseEntity.ok(ApiResponse.success(subscription));
    }

    /**
     * ==========================================================================
     * GET ALL SUBSCRIPTION PLANS
     * ==========================================================================
     * Returns all available subscription plans with pricing and features.
     * Public endpoint - no authentication required.
     * 
     * ENDPOINT: GET /api/v1/subscription/plans
     * AUTH: None
     * ==========================================================================
     */
    @GetMapping("/subscription/plans")
    @Operation(
        summary = "Get all subscription plans",
        description = "List all available subscription plans with pricing and features"
    )
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> getAllPlans() {
        log.debug("Getting all subscription plans");
        List<SubscriptionPlanDto> plans = subscriptionService.getAllPlans();
        return ResponseEntity.ok(ApiResponse.success(plans));
    }

    /**
     * ==========================================================================
     * GET PLANS WITH CURRENT MARKED
     * ==========================================================================
     * Returns all plans with the current tenant's plan marked.
     * Used for plan comparison/upgrading.
     * 
     * ENDPOINT: GET /api/v1/subscription/plans/compare
     * AUTH: Requires JWT token
     * ==========================================================================
     */
    @GetMapping("/subscription/plans/compare")
    @Operation(
        summary = "Get plans for comparison",
        description = "Get all plans with current plan marked for comparison",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> getPlansForComparison() {
        log.debug("Getting plans with current marked");
        List<SubscriptionPlanDto> plans = subscriptionService.getPlansWithCurrentMarked();
        return ResponseEntity.ok(ApiResponse.success(plans));
    }

    /**
     * ==========================================================================
     * GET SPECIFIC PLAN
     * ==========================================================================
     * Returns details for a specific subscription plan.
     * Public endpoint - no authentication required.
     * 
     * ENDPOINT: GET /api/v1/subscription/plans/{plan}
     * AUTH: None
     * ==========================================================================
     */
    @GetMapping("/subscription/plans/{plan}")
    @Operation(
        summary = "Get plan details",
        description = "Get details for a specific subscription plan"
    )
    public ResponseEntity<ApiResponse<SubscriptionPlanDto>> getPlan(
            @Parameter(description = "Plan name (STARTER, BUSINESS, ENTERPRISE)", required = true, example = "BUSINESS")
            @PathVariable String plan) {
        log.debug("Getting plan details for: {}", plan);
        
        try {
            SubscriptionPlan subscriptionPlan = SubscriptionPlan.fromString(plan);
            SubscriptionPlanDto planDto = subscriptionService.getPlan(subscriptionPlan);
            return ResponseEntity.ok(ApiResponse.success(planDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<SubscriptionPlanDto>error("Invalid plan name: " + plan));
        }
    }

    /**
     * ==========================================================================
     * UPGRADE SUBSCRIPTION
     * ==========================================================================
     * Upgrades the current tenant to a higher plan.
     * Note: Payment processing would be integrated here.
     * 
     * ENDPOINT: POST /api/v1/tenants/subscription/upgrade
     * AUTH: Requires JWT token + Admin role
     * ==========================================================================
     */
    @PostMapping("/tenants/subscription/upgrade")
    @Operation(
        summary = "Upgrade subscription",
        description = "Upgrade to a higher subscription plan (Admin only)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<SubscriptionDto>> upgradeSubscription(
            @Parameter(description = "New plan to upgrade to", required = true)
            @RequestParam String plan) {
        log.info("Upgrading subscription to plan: {}", plan);
        
        try {
            SubscriptionPlan newPlan = SubscriptionPlan.fromString(plan);
            
            // Prevent "upgrading" to same or lower plan
            SubscriptionDto current = subscriptionService.getCurrentSubscription();
            SubscriptionPlan currentPlan = SubscriptionPlan.valueOf(current.getPlan());
            
            if (newPlan.ordinal() <= currentPlan.ordinal()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<SubscriptionDto>error(
                            "Cannot upgrade to same or lower plan. Current: " + currentPlan.getDisplayName()));
            }
            
            // TODO: Integrate payment processing here
            // For now, we just update the plan
            
            SubscriptionDto updated = subscriptionService.upgradePlan(newPlan);
            return ResponseEntity.ok(ApiResponse.success("Subscription upgraded successfully", updated));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<SubscriptionDto>error("Invalid plan name: " + plan));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<SubscriptionDto>error(e.getMessage()));
        }
    }

    /**
     * ==========================================================================
     * CANCEL SUBSCRIPTION
     * ==========================================================================
     * Cancels the current subscription (downgrades to STARTER at period end).
     * The tenant retains access until the current period ends.
     * 
     * ENDPOINT: POST /api/v1/tenants/subscription/cancel
     * AUTH: Requires JWT token + Admin role
     * ==========================================================================
     */
    @PostMapping("/tenants/subscription/cancel")
    @Operation(
        summary = "Cancel subscription",
        description = "Cancel subscription (downgrades to STARTER at period end)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<SubscriptionDto>> cancelSubscription() {
        log.info("Cancelling subscription");
        
        try {
            SubscriptionDto cancelled = subscriptionService.cancelSubscription();
            String message = "Subscription will be cancelled at the end of the current billing period. " +
                           "You will be downgraded to the Starter plan.";
            return ResponseEntity.ok(ApiResponse.success(message, cancelled));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<SubscriptionDto>error(e.getMessage()));
        }
    }

    /**
     * ==========================================================================
     * CHECK FEATURE ACCESS
     * ==========================================================================
     * Checks if a specific feature is available on the current plan.
     * 
     * ENDPOINT: GET /api/v1/tenants/subscription/features/{feature}
     * AUTH: Requires JWT token
     * ==========================================================================
     */
    @GetMapping("/tenants/subscription/features/{feature}")
    @Operation(
        summary = "Check feature access",
        description = "Check if a specific feature is available on the current plan",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Boolean>> checkFeatureAccess(
            @Parameter(description = "Feature name", required = true, example = "REPORTS")
            @PathVariable String feature) {
        try {
            Feature featureEnum = Feature.valueOf(feature.toUpperCase());
            boolean hasAccess = subscriptionService.hasFeatureAccess(featureEnum);
            return ResponseEntity.ok(ApiResponse.success(hasAccess));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Boolean>error("Invalid feature name: " + feature));
        }
    }

    /**
     * ==========================================================================
     * RECALCULATE USAGE
     * ==========================================================================
     * Recalculates actual usage from database counts.
     * Useful for fixing discrepancies.
     * 
     * ENDPOINT: POST /api/v1/tenants/subscription/recalculate
     * AUTH: Requires JWT token + Admin role
     * ==========================================================================
     */
    @PostMapping("/tenants/subscription/recalculate")
    @Operation(
        summary = "Recalculate usage",
        description = "Recalculate usage counts from actual database values (Admin only)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> recalculateUsage() {
        log.info("Recalculating usage");
        subscriptionService.recalculateUsage();
        return ResponseEntity.ok(ApiResponse.success("Usage recalculated successfully", null));
    }
}
