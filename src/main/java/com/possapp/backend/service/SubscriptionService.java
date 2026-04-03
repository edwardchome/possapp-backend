package com.possapp.backend.service;

import com.possapp.backend.dto.*;
import com.possapp.backend.entity.*;
import com.possapp.backend.exception.SubscriptionLimitExceededException;
import com.possapp.backend.repository.*;
import com.possapp.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * SUBSCRIPTION SERVICE
 * ============================================================================
 * Core service for subscription management including:
 * - Retrieving subscription details
 * - Checking feature access and resource limits
 * - Enforcing subscription constraints
 * - Plan upgrades and downgrades
 * ============================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final TenantRepository tenantRepository;
    private final SubscriptionConfigRepository configRepository;
    private final TenantUsageRepository usageRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    // ==================== SUBSCRIPTION DETAILS ====================

    /**
     * Get current subscription details for the authenticated tenant.
     */
    public SubscriptionDto getCurrentSubscription() {
        String schemaName = TenantContext.getCurrentTenant();
        if (schemaName == null) {
            throw new IllegalStateException("No tenant context found");
        }
        
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + schemaName));
        
        return buildSubscriptionDto(tenant);
    }

    /**
     * Get subscription details for a specific tenant by ID.
     */
    public SubscriptionDto getSubscriptionByTenantId(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId.toString())
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));
        
        return buildSubscriptionDto(tenant);
    }

    /**
     * Build subscription DTO from tenant entity.
     */
    private SubscriptionDto buildSubscriptionDto(Tenant tenant) {
        SubscriptionConfig config = getConfig(tenant.getSubscriptionPlan());
        TenantUsage usage = getOrCreateUsage(tenant.getId());
        
        // Calculate days until expiry
        Integer daysUntilExpiry = null;
        if (tenant.getCurrentPeriodEnd() != null) {
            daysUntilExpiry = (int) ChronoUnit.DAYS.between(
                    LocalDateTime.now(), tenant.getCurrentPeriodEnd());
        }
        
        // Check if needs renewal (expires within 7 days)
        boolean needsRenewal = daysUntilExpiry != null && daysUntilExpiry <= 7 && daysUntilExpiry >= 0;
        
        // Suggest next plan if at limits
        SubscriptionPlan suggestedPlan = null;
        if (!config.hasUnlimitedUsers() && usage.getCurrentUsers() >= config.getMaxUsers()) {
            suggestedPlan = getNextPlan(tenant.getSubscriptionPlan());
        } else if (!config.hasUnlimitedBranches() && usage.getCurrentBranches() >= config.getMaxBranches()) {
            suggestedPlan = getNextPlan(tenant.getSubscriptionPlan());
        }
        
        return SubscriptionDto.builder()
                .plan(tenant.getSubscriptionPlan().name())
                .status(tenant.getSubscriptionStatus().name())
                .displayName(config.getDisplayName())
                .currentPeriodStart(tenant.getSubscriptionStartedAt())
                .currentPeriodEnd(tenant.getCurrentPeriodEnd())
                .trialEndsAt(tenant.getTrialEndsAt())
                .daysUntilExpiry(daysUntilExpiry != null && daysUntilExpiry < 0 ? 0 : daysUntilExpiry)
                .usage(buildUsageDto(config, usage))
                .features(buildFeatureFlags(config))
                .canUpgrade(tenant.getSubscriptionPlan() != SubscriptionPlan.ENTERPRISE)
                .canCancel(tenant.getSubscriptionPlan() != SubscriptionPlan.STARTER)
                .isInTrial(tenant.isInTrial())
                .needsRenewal(needsRenewal)
                .suggestedPlan(suggestedPlan != null ? suggestedPlan.name() : null)
                .suggestedPlanDisplayName(suggestedPlan != null ? suggestedPlan.getDisplayName() : null)
                .build();
    }

    /**
     * Build usage DTO from config and usage entities.
     */
    private UsageDto buildUsageDto(SubscriptionConfig config, TenantUsage usage) {
        return UsageDto.builder()
                .users(UsageDto.ResourceUsage.of(usage.getCurrentUsers(), config.getMaxUsers()))
                .branches(UsageDto.ResourceUsage.of(usage.getCurrentBranches(), config.getMaxBranches()))
                .products(UsageDto.ResourceUsage.of(usage.getCurrentProducts(), config.getMaxProducts()))
                .monthlyTransactions(UsageDto.ResourceUsage.of(
                        usage.getCurrentMonthlyTransactions(), config.getMaxMonthlyTransactions()))
                .build();
    }

    /**
     * Build feature flags DTO from config.
     */
    private FeatureFlagsDto buildFeatureFlags(SubscriptionConfig config) {
        return FeatureFlagsDto.builder()
                .basicPos(true)
                .basicInventory(true)
                .reports(config.isFeatureReports())
                .barcode(config.isFeatureBarcode())
                .analytics(config.isFeatureAnalytics())
                .multiBranch(config.isFeatureMultiBranch())
                .userManagement(true) // Available on all plans with limits
                .apiAccess(config.isFeatureApiAccess())
                .customIntegrations(config.isFeatureCustomIntegrations())
                .prioritySupport(config.getPlanName() == SubscriptionPlan.ENTERPRISE)
                .whiteLabel(config.getPlanName() == SubscriptionPlan.ENTERPRISE)
                .build();
    }

    // ==================== PLAN CONFIGURATION ====================

    /**
     * Get all available subscription plans.
     */
    public List<SubscriptionPlanDto> getAllPlans() {
        return configRepository.findAllByOrderByPlanNameAsc().stream()
                .map(this::mapToPlanDto)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific plan by name.
     */
    public SubscriptionPlanDto getPlan(SubscriptionPlan plan) {
        SubscriptionConfig config = getConfig(plan);
        return mapToPlanDto(config);
    }

    /**
     * Get all plans with current plan marked.
     */
    public List<SubscriptionPlanDto> getPlansWithCurrentMarked() {
        SubscriptionDto current = getCurrentSubscription();
        SubscriptionPlan currentPlan = SubscriptionPlan.valueOf(current.getPlan());
        
        return configRepository.findAllByOrderByPlanNameAsc().stream()
                .map(config -> {
                    SubscriptionPlanDto dto = mapToPlanDto(config);
                    dto.setCurrentPlan(config.getPlanName() == currentPlan);
                    dto.setCanUpgradeTo(config.getPlanName().ordinal() > currentPlan.ordinal());
                    dto.setCanDowngradeTo(config.getPlanName().ordinal() < currentPlan.ordinal());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Map config entity to DTO.
     */
    private SubscriptionPlanDto mapToPlanDto(SubscriptionConfig config) {
        return SubscriptionPlanDto.builder()
                .plan(config.getPlanName().name())
                .displayName(config.getDisplayName())
                .description(config.getDescription())
                .monthlyPrice(config.getMonthlyPrice())
                .yearlyPrice(config.getYearlyPrice())
                .maxUsers(config.getMaxUsers())
                .maxBranches(config.getMaxBranches())
                .maxProducts(config.getMaxProducts())
                .maxMonthlyTransactions(config.getMaxMonthlyTransactions())
                .features(buildFeatureFlags(config))
                .upgradePriority(config.getPlanName().ordinal())
                .build()
                .buildDisplayStrings();
    }

    /**
     * Get subscription configuration for a plan.
     */
    public SubscriptionConfig getConfig(SubscriptionPlan plan) {
        return configRepository.findByPlanName(plan)
                .orElseGet(() -> createDefaultConfig(plan));
    }

    /**
     * Create default config if not found in database.
     */
    private SubscriptionConfig createDefaultConfig(SubscriptionPlan plan) {
        return switch (plan) {
            case STARTER -> SubscriptionConfig.builder()
                    .planName(SubscriptionPlan.STARTER)
                    .displayName("Starter")
                    .description("Perfect for small businesses just getting started")
                    .maxUsers(2)
                    .maxBranches(1)
                    .maxProducts(100)
                    .monthlyPrice(BigDecimal.ZERO)
                    .yearlyPrice(BigDecimal.ZERO)
                    .build();
            case BUSINESS -> SubscriptionConfig.builder()
                    .planName(SubscriptionPlan.BUSINESS)
                    .displayName("Business")
                    .description("For growing businesses with multiple users")
                    .maxUsers(5)
                    .maxBranches(3)
                    .maxProducts(1000)
                    .featureReports(true)
                    .featureBarcode(true)
                    .featureMultiBranch(true)
                    .monthlyPrice(new BigDecimal("29.99"))
                    .yearlyPrice(new BigDecimal("299.99"))
                    .build();
            case ENTERPRISE -> SubscriptionConfig.builder()
                    .planName(SubscriptionPlan.ENTERPRISE)
                    .displayName("Enterprise")
                    .description("Unlimited everything for large operations")
                    .maxUsers(-1)
                    .maxBranches(-1)
                    .maxProducts(null)
                    .featureReports(true)
                    .featureBarcode(true)
                    .featureMultiBranch(true)
                    .featureAnalytics(true)
                    .featureApiAccess(true)
                    .featureCustomIntegrations(true)
                    .monthlyPrice(new BigDecimal("99.99"))
                    .yearlyPrice(new BigDecimal("999.99"))
                    .build();
        };
    }

    // ==================== LIMIT ENFORCEMENT ====================

    /**
     * Check if tenant can create a new user.
     */
    public boolean canCreateUser() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        SubscriptionConfig config = getConfig(tenant.getSubscriptionPlan());
        TenantUsage usage = getOrCreateUsage(tenant.getId());
        
        return !config.isAtLimit(LimitType.USER, usage.getCurrentUsers());
    }

    /**
     * Check if tenant can create a new branch.
     */
    public boolean canCreateBranch() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        SubscriptionConfig config = getConfig(tenant.getSubscriptionPlan());
        TenantUsage usage = getOrCreateUsage(tenant.getId());
        
        return !config.isAtLimit(LimitType.BRANCH, usage.getCurrentBranches());
    }

    /**
     * Check if tenant can create a new product.
     */
    public boolean canCreateProduct() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        SubscriptionConfig config = getConfig(tenant.getSubscriptionPlan());
        TenantUsage usage = getOrCreateUsage(tenant.getId());
        
        return !config.isAtLimit(LimitType.PRODUCT, usage.getCurrentProducts());
    }

    /**
     * Enforce user limit or throw exception.
     */
    public void enforceUserLimit() {
        String schemaName = TenantContext.getCurrentTenant();
        log.info("🔍 Checking user limit for tenant: {}", schemaName);
        
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        SubscriptionConfig config = getConfig(tenant.getSubscriptionPlan());
        TenantUsage usage = getOrCreateUsage(tenant.getId());
        
        int currentUsers = usage.getCurrentUsers();
        Integer maxUsers = config.getMaxUsers();
        
        log.info("📊 User usage: {}/{} (plan: {})", currentUsers, 
                maxUsers == null ? "unlimited" : maxUsers,
                tenant.getSubscriptionPlan());
        
        if (config.isAtLimit(LimitType.USER, currentUsers)) {
            log.warn("🚫 User limit exceeded: {}/{} on {} plan", currentUsers, maxUsers, tenant.getSubscriptionPlan());
            throw new SubscriptionLimitExceededException(
                    tenant.getId().toString(),
                    LimitType.USER,
                    currentUsers,
                    maxUsers,
                    tenant.getSubscriptionPlan()
            );
        }
        log.info("✅ User limit check passed");
    }

    /**
     * Enforce branch limit or throw exception.
     */
    public void enforceBranchLimit() {
        String schemaName = TenantContext.getCurrentTenant();
        log.info("🔍 Checking branch limit for tenant: {}", schemaName);
        
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        SubscriptionConfig config = getConfig(tenant.getSubscriptionPlan());
        TenantUsage usage = getOrCreateUsage(tenant.getId());
        
        int currentBranches = usage.getCurrentBranches();
        Integer maxBranches = config.getMaxBranches();
        
        log.info("📊 Branch usage: {}/{} (plan: {})", currentBranches,
                maxBranches == null ? "unlimited" : maxBranches,
                tenant.getSubscriptionPlan());
        
        if (config.isAtLimit(LimitType.BRANCH, currentBranches)) {
            log.warn("🚫 Branch limit exceeded: {}/{} on {} plan", currentBranches, maxBranches, tenant.getSubscriptionPlan());
            throw new SubscriptionLimitExceededException(
                    tenant.getId().toString(),
                    LimitType.BRANCH,
                    currentBranches,
                    maxBranches,
                    tenant.getSubscriptionPlan()
            );
        }
        log.info("✅ Branch limit check passed");
    }

    /**
     * Enforce product limit or throw exception.
     */
    public void enforceProductLimit() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        SubscriptionConfig config = getConfig(tenant.getSubscriptionPlan());
        TenantUsage usage = getOrCreateUsage(tenant.getId());
        
        if (config.isAtLimit(LimitType.PRODUCT, usage.getCurrentProducts())) {
            throw new SubscriptionLimitExceededException(
                    tenant.getId().toString(),
                    LimitType.PRODUCT,
                    usage.getCurrentProducts(),
                    config.getMaxProducts(),
                    tenant.getSubscriptionPlan()
            );
        }
    }

    // ==================== FEATURE ACCESS ====================

    /**
     * Check if current tenant has access to a feature.
     */
    public boolean hasFeatureAccess(Feature feature) {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        return feature.isAvailableFor(tenant.getSubscriptionPlan());
    }

    /**
     * Check feature access for reports.
     */
    public boolean canAccessReports() {
        return hasFeatureAccess(Feature.REPORTS);
    }

    /**
     * Check feature access for barcode scanning.
     */
    public boolean canUseBarcode() {
        return hasFeatureAccess(Feature.BARCODE_SCANNING);
    }

    /**
     * Check feature access for analytics.
     */
    public boolean canAccessAnalytics() {
        return hasFeatureAccess(Feature.ANALYTICS);
    }

    /**
     * Check feature access for multi-branch.
     */
    public boolean canUseMultiBranch() {
        return hasFeatureAccess(Feature.MULTI_BRANCH);
    }

    // ==================== USAGE TRACKING ====================

    /**
     * Get or create usage record for tenant.
     */
    public TenantUsage getOrCreateUsage(UUID tenantId) {
        return usageRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantUsage newUsage = TenantUsage.builder()
                            .tenantId(tenantId)
                            .currentUsers(0)
                            .currentBranches(0)
                            .currentProducts(0)
                            .currentMonthlyTransactions(0)
                            .build();
                    return usageRepository.save(newUsage);
                });
    }

    /**
     * Increment user count.
     */
    @Transactional
    public void incrementUserCount() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        usageRepository.incrementUserCount(tenant.getId());
    }

    /**
     * Decrement user count.
     */
    @Transactional
    public void decrementUserCount() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        usageRepository.decrementUserCount(tenant.getId());
    }

    /**
     * Increment branch count.
     */
    @Transactional
    public void incrementBranchCount() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        usageRepository.incrementBranchCount(tenant.getId());
    }

    /**
     * Decrement branch count.
     */
    @Transactional
    public void decrementBranchCount() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        usageRepository.decrementBranchCount(tenant.getId());
    }

    /**
     * Increment product count.
     */
    @Transactional
    public void incrementProductCount() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        usageRepository.incrementProductCount(tenant.getId());
    }

    /**
     * Decrement product count.
     */
    @Transactional
    public void decrementProductCount() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        usageRepository.decrementProductCount(tenant.getId());
    }

    /**
     * Recalculate all usage counts for current tenant.
     */
    @Transactional
    public void recalculateUsage() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        TenantUsage usage = getOrCreateUsage(tenant.getId());
        
        // Count users
        long userCount = userRepository.count();
        usage.setCurrentUsers((int) userCount);
        
        // Count branches
        long branchCount = branchRepository.count();
        usage.setCurrentBranches((int) branchCount);
        
        usageRepository.save(usage);
        
        log.info("Recalculated usage for tenant {}: {} users, {} branches", 
                schemaName, userCount, branchCount);
    }

    // ==================== PLAN CHANGES ====================

    /**
     * Upgrade tenant to a higher plan.
     */
    @Transactional
    public SubscriptionDto upgradePlan(SubscriptionPlan newPlan) {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        if (tenant.getSubscriptionPlan().ordinal() >= newPlan.ordinal()) {
            throw new IllegalArgumentException("Cannot upgrade to same or lower plan");
        }
        
        SubscriptionPlan oldPlan = tenant.getSubscriptionPlan();
        tenant.setSubscriptionPlan(newPlan);
        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        
        // Set period end (30 days from now for monthly, or calculate based on billing)
        tenant.setCurrentPeriodEnd(LocalDateTime.now().plusDays(30));
        
        if (tenant.getSubscriptionStartedAt() == null) {
            tenant.setSubscriptionStartedAt(LocalDateTime.now());
        }
        
        tenantRepository.save(tenant);
        
        log.info("Tenant {} upgraded from {} to {}", schemaName, oldPlan, newPlan);
        
        return buildSubscriptionDto(tenant);
    }

    /**
     * Cancel subscription (downgrade to STARTER at period end).
     */
    @Transactional
    public SubscriptionDto cancelSubscription() {
        String schemaName = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));
        
        if (tenant.getSubscriptionPlan() == SubscriptionPlan.STARTER) {
            throw new IllegalArgumentException("Cannot cancel free plan");
        }
        
        tenant.setCancelAtPeriodEnd(true);
        tenant.setCancelledAt(LocalDateTime.now());
        tenantRepository.save(tenant);
        
        log.info("Tenant {} scheduled cancellation at period end", schemaName);
        
        return buildSubscriptionDto(tenant);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get the next higher plan.
     */
    private SubscriptionPlan getNextPlan(SubscriptionPlan current) {
        return switch (current) {
            case STARTER -> SubscriptionPlan.BUSINESS;
            case BUSINESS -> SubscriptionPlan.ENTERPRISE;
            case ENTERPRISE -> SubscriptionPlan.ENTERPRISE;
        };
    }
}
