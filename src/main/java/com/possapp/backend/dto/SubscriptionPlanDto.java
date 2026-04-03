package com.possapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ============================================================================
 * SUBSCRIPTION PLAN DTO
 * ============================================================================
 * Represents a subscription plan's configuration and pricing.
 * Used in plan listing and comparison endpoints.
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionPlanDto {

    private String plan;
    private String displayName;
    private String description;
    
    // Pricing
    private BigDecimal monthlyPrice;
    private BigDecimal yearlyPrice;
    private BigDecimal savings; // Calculated: monthly * 12 - yearly
    private Integer savingsPercentage;
    
    // Limits
    private Integer maxUsers;
    private Integer maxBranches;
    private Integer maxProducts;
    private Integer maxMonthlyTransactions;
    
    // Human-readable limits for display
    private String usersDisplay;
    private String branchesDisplay;
    private String productsDisplay;
    
    // Features
    private FeatureFlagsDto features;
    
    // Plan hierarchy
    private boolean isCurrentPlan;
    private boolean canUpgradeTo;
    private boolean canDowngradeTo;
    private Integer upgradePriority; // For sorting (0 = Starter, 1 = Business, 2 = Enterprise)

    /**
     * Get formatted price display.
     */
    public String getMonthlyPriceDisplay() {
        if (monthlyPrice == null || monthlyPrice.compareTo(BigDecimal.ZERO) == 0) {
            return "Free";
        }
        return "$" + monthlyPrice.toString();
    }

    /**
     * Get formatted yearly price display.
     */
    public String getYearlyPriceDisplay() {
        if (yearlyPrice == null || yearlyPrice.compareTo(BigDecimal.ZERO) == 0) {
            return "Free";
        }
        return "$" + yearlyPrice.toString();
    }

    /**
     * Format limit value for display.
     */
    private static String formatLimit(Integer limit) {
        if (limit == null || limit < 0) {
            return "Unlimited";
        }
        return String.valueOf(limit);
    }

    /**
     * Build display strings from limits.
     */
    public SubscriptionPlanDto buildDisplayStrings() {
        this.usersDisplay = formatLimit(maxUsers);
        this.branchesDisplay = formatLimit(maxBranches);
        this.productsDisplay = formatLimit(maxProducts);
        
        // Calculate savings
        if (monthlyPrice != null && yearlyPrice != null && 
            monthlyPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal monthlyTotal = monthlyPrice.multiply(new BigDecimal("12"));
            this.savings = monthlyTotal.subtract(yearlyPrice);
            if (monthlyTotal.compareTo(BigDecimal.ZERO) > 0) {
                this.savingsPercentage = savings.multiply(new BigDecimal("100"))
                        .divide(monthlyTotal, 0, BigDecimal.ROUND_HALF_UP)
                        .intValue();
            }
        }
        
        return this;
    }
}
