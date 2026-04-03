package com.possapp.backend.repository;

import com.possapp.backend.entity.SubscriptionConfig;
import com.possapp.backend.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * SUBSCRIPTION CONFIG REPOSITORY
 * ============================================================================
 * Repository for accessing subscription plan configurations.
 * All data is in the public schema since plans are global.
 * ============================================================================
 */
@Repository
public interface SubscriptionConfigRepository extends JpaRepository<SubscriptionConfig, String> {

    /**
     * Find configuration by plan name.
     */
    Optional<SubscriptionConfig> findByPlanName(SubscriptionPlan planName);

    /**
     * Find all active subscription configurations.
     */
    List<SubscriptionConfig> findByActiveTrue();

    /**
     * Find all configurations ordered by plan hierarchy.
     */
    List<SubscriptionConfig> findAllByOrderByPlanNameAsc();

    /**
     * Check if a configuration exists for a plan.
     */
    boolean existsByPlanName(SubscriptionPlan planName);
}
