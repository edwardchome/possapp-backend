package com.possapp.backend.repository;

import com.possapp.backend.entity.TenantUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * ============================================================================
 * TENANT USAGE REPOSITORY
 * ============================================================================
 * Repository for accessing tenant resource usage data.
 * All data is in the public schema.
 * ============================================================================
 */
@Repository
public interface TenantUsageRepository extends JpaRepository<TenantUsage, UUID> {

    /**
     * Find usage record by tenant ID.
     */
    Optional<TenantUsage> findByTenantId(UUID tenantId);

    /**
     * Check if usage record exists for tenant.
     */
    boolean existsByTenantId(UUID tenantId);

    /**
     * Increment user count for a tenant.
     */
    @Modifying
    @Query("UPDATE TenantUsage tu SET tu.currentUsers = tu.currentUsers + 1, tu.calculatedAt = CURRENT_TIMESTAMP WHERE tu.tenantId = :tenantId")
    int incrementUserCount(@Param("tenantId") UUID tenantId);

    /**
     * Decrement user count for a tenant.
     */
    @Modifying
    @Query("UPDATE TenantUsage tu SET tu.currentUsers = GREATEST(0, tu.currentUsers - 1), tu.calculatedAt = CURRENT_TIMESTAMP WHERE tu.tenantId = :tenantId")
    int decrementUserCount(@Param("tenantId") UUID tenantId);

    /**
     * Increment branch count for a tenant.
     */
    @Modifying
    @Query("UPDATE TenantUsage tu SET tu.currentBranches = tu.currentBranches + 1, tu.calculatedAt = CURRENT_TIMESTAMP WHERE tu.tenantId = :tenantId")
    int incrementBranchCount(@Param("tenantId") UUID tenantId);

    /**
     * Decrement branch count for a tenant.
     */
    @Modifying
    @Query("UPDATE TenantUsage tu SET tu.currentBranches = GREATEST(0, tu.currentBranches - 1), tu.calculatedAt = CURRENT_TIMESTAMP WHERE tu.tenantId = :tenantId")
    int decrementBranchCount(@Param("tenantId") UUID tenantId);

    /**
     * Increment product count for a tenant.
     */
    @Modifying
    @Query("UPDATE TenantUsage tu SET tu.currentProducts = tu.currentProducts + 1, tu.calculatedAt = CURRENT_TIMESTAMP WHERE tu.tenantId = :tenantId")
    int incrementProductCount(@Param("tenantId") UUID tenantId);

    /**
     * Decrement product count for a tenant.
     */
    @Modifying
    @Query("UPDATE TenantUsage tu SET tu.currentProducts = GREATEST(0, tu.currentProducts - 1), tu.calculatedAt = CURRENT_TIMESTAMP WHERE tu.tenantId = :tenantId")
    int decrementProductCount(@Param("tenantId") UUID tenantId);

    /**
     * Increment monthly transaction count for a tenant.
     */
    @Modifying
    @Query("UPDATE TenantUsage tu SET tu.currentMonthlyTransactions = tu.currentMonthlyTransactions + 1, tu.calculatedAt = CURRENT_TIMESTAMP WHERE tu.tenantId = :tenantId")
    int incrementTransactionCount(@Param("tenantId") UUID tenantId);

    /**
     * Reset monthly transaction counts for all tenants.
     * Should be run at the start of each month.
     */
    @Modifying
    @Query("UPDATE TenantUsage tu SET tu.currentMonthlyTransactions = 0, tu.calculatedAt = CURRENT_TIMESTAMP")
    int resetAllMonthlyTransactions();

    /**
     * Update calculated timestamp for a tenant.
     */
    @Modifying
    @Query("UPDATE TenantUsage tu SET tu.calculatedAt = CURRENT_TIMESTAMP WHERE tu.tenantId = :tenantId")
    int updateCalculatedAt(@Param("tenantId") UUID tenantId);
}
