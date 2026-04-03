package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ============================================================================
 * SUBSCRIPTION CONFIG ENTITY
 * ============================================================================
 * Stores the configuration for each subscription plan tier.
 * This is a public schema table that defines the limits and features
 * available for each plan (STARTER, BUSINESS, ENTERPRISE).
 * 
 * Note: Values of -1 for numeric limits indicate "unlimited".
 * ============================================================================
 */
@Entity
@Table(name = "subscription_limits", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_name", nullable = false, unique = true)
    @Enumerated(EnumType.STRING)
    private SubscriptionPlan planName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description")
    private String description;

    // Resource Limits
    @Column(name = "max_users", nullable = false)
    private Integer maxUsers;

    @Column(name = "max_branches", nullable = false)
    private Integer maxBranches;

    @Column(name = "max_products")
    private Integer maxProducts;

    @Column(name = "max_monthly_transactions")
    private Integer maxMonthlyTransactions;

    // Feature Flags
    @Column(name = "feature_reports", nullable = false)
    @Builder.Default
    private boolean featureReports = false;

    @Column(name = "feature_barcode", nullable = false)
    @Builder.Default
    private boolean featureBarcode = false;

    @Column(name = "feature_analytics", nullable = false)
    @Builder.Default
    private boolean featureAnalytics = false;

    @Column(name = "feature_multi_branch", nullable = false)
    @Builder.Default
    private boolean featureMultiBranch = false;

    @Column(name = "feature_api_access", nullable = false)
    @Builder.Default
    private boolean featureApiAccess = false;

    @Column(name = "feature_custom_integrations", nullable = false)
    @Builder.Default
    private boolean featureCustomIntegrations = false;

    // Pricing
    @Column(name = "monthly_price", precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "yearly_price", precision = 10, scale = 2)
    private BigDecimal yearlyPrice;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Helper methods

    /**
     * Check if this plan has unlimited users.
     */
    public boolean hasUnlimitedUsers() {
        return LimitType.isUnlimited(maxUsers);
    }

    /**
     * Check if this plan has unlimited branches.
     */
    public boolean hasUnlimitedBranches() {
        return LimitType.isUnlimited(maxBranches);
    }

    /**
     * Check if this plan has unlimited products.
     */
    public boolean hasUnlimitedProducts() {
        return LimitType.isUnlimited(maxProducts);
    }

    /**
     * Check if the specified feature is enabled for this plan.
     */
    public boolean hasFeature(Feature feature) {
        return switch (feature) {
            case BASIC_POS, BASIC_INVENTORY -> true; // Always available
            case REPORTS -> featureReports;
            case BARCODE_SCANNING -> true; // Available on all plans (since V19)
            case MULTI_BRANCH -> featureMultiBranch;
            case USER_MANAGEMENT -> true; // Available on all plans but with limits
            case ANALYTICS -> featureAnalytics;
            case API_ACCESS -> featureApiAccess;
            case CUSTOM_INTEGRATIONS -> featureCustomIntegrations;
            case PRIORITY_SUPPORT, WHITE_LABEL -> planName == SubscriptionPlan.ENTERPRISE;
        };
    }

    /**
     * Get the limit for a specific resource type.
     */
    public Integer getLimit(LimitType type) {
        return switch (type) {
            case USER -> maxUsers;
            case BRANCH -> maxBranches;
            case PRODUCT -> maxProducts;
            case MONTHLY_TRANSACTION -> maxMonthlyTransactions;
        };
    }

    /**
     * Check if a specific limit is unlimited.
     */
    public boolean isUnlimited(LimitType type) {
        return LimitType.isUnlimited(getLimit(type));
    }

    /**
     * Check if current usage has reached the limit for a resource type.
     */
    public boolean isAtLimit(LimitType type, int currentUsage) {
        return LimitType.isAtLimit(currentUsage, getLimit(type));
    }

    /**
     * Check if current usage has exceeded the limit for a resource type.
     */
    public boolean isExceeded(LimitType type, int currentUsage) {
        return LimitType.isExceeded(currentUsage, getLimit(type));
    }
}
