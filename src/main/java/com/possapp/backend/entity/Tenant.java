package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ============================================================================
 * TENANT ENTITY
 * ============================================================================
 * Represents a business/tenant in the multi-tenant system.
 * Each tenant gets their own database schema for data isolation.
 * 
 * SUBSCRIPTION FIELDS:
 * - subscriptionPlan: STARTER, BUSINESS, or ENTERPRISE
 * - subscriptionStatus: ACTIVE, EXPIRED, CANCELLED, etc.
 * - currentPeriodEnd: When the current billing period ends
 * - subscriptionStartedAt: When the tenant first subscribed
 * - trialEndsAt: End of trial period (if applicable)
 * ============================================================================
 */
@Entity
@Table(name = "tenants", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "company_name", nullable = false)
    private String companyName;
    
    @Column(name = "schema_name", nullable = false, unique = true)
    private String schemaName;
    
    @Column(name = "admin_email", nullable = false, unique = true)
    private String adminEmail;
    
    @Column(name = "contact_phone")
    private String contactPhone;
    
    @Column(name = "address")
    private String address;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
    
    // ==================== SUBSCRIPTION FIELDS ====================
    
    @Column(name = "subscription_plan", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.STARTER;
    
    @Column(name = "subscription_status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.ACTIVE;
    
    @Column(name = "subscription_started_at")
    private LocalDateTime subscriptionStartedAt;
    
    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;
    
    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "cancel_at_period_end", nullable = false)
    @Builder.Default
    private boolean cancelAtPeriodEnd = false;
    
    @Column(name = "subscription_expires_at")
    private LocalDateTime subscriptionExpiresAt;
    
    // ==================== TIMESTAMP FIELDS ====================
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Check if the tenant's subscription is currently active/operational.
     */
    public boolean isSubscriptionActive() {
        return subscriptionStatus != null && subscriptionStatus.isOperational();
    }
    
    /**
     * Check if the tenant's subscription allows write operations.
     */
    public boolean canWrite() {
        return subscriptionStatus != null && subscriptionStatus.canWrite();
    }
    
    /**
     * Check if the tenant's subscription allows read operations.
     */
    public boolean canRead() {
        return subscriptionStatus != null && subscriptionStatus.canRead();
    }
    
    /**
     * Check if the tenant is on the specified plan or higher.
     */
    public boolean isOnPlanOrHigher(SubscriptionPlan plan) {
        return subscriptionPlan != null && subscriptionPlan.isAtLeast(plan);
    }
    
    /**
     * Check if the tenant has access to a specific feature.
     */
    public boolean hasFeatureAccess(Feature feature) {
        return subscriptionPlan != null && feature.isAvailableFor(subscriptionPlan);
    }
    
    /**
     * Check if the tenant is in trial mode.
     */
    public boolean isInTrial() {
        return subscriptionStatus == SubscriptionStatus.TRIAL && 
               trialEndsAt != null && trialEndsAt.isAfter(LocalDateTime.now());
    }
    
    /**
     * Check if the subscription is expired.
     */
    public boolean isExpired() {
        if (subscriptionStatus == SubscriptionStatus.EXPIRED) {
            return true;
        }
        if (currentPeriodEnd != null && currentPeriodEnd.isBefore(LocalDateTime.now())) {
            return !isInTrial(); // Trial extends the period
        }
        return false;
    }
    
    /**
     * Get the plan as a String (for backward compatibility).
     */
    public String getSubscriptionPlanString() {
        return subscriptionPlan != null ? subscriptionPlan.name() : SubscriptionPlan.STARTER.name();
    }
    
    /**
     * Set the plan from a String (for backward compatibility).
     */
    public void setSubscriptionPlanString(String plan) {
        this.subscriptionPlan = SubscriptionPlan.fromString(plan);
    }
}
