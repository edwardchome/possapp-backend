package com.possapp.backend.repository;

import com.possapp.backend.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * BRANCH REPOSITORY - Data Access Layer for Branch Management
 * ============================================================================
 *
 * Repository interface for CRUD operations on Branch entities.
 * All queries are automatically filtered by the current tenant schema
 * through the Hibernate TenantIdentifierResolver.
 *
 * KEY METHODS:
 * - Find active branches for dropdowns
 * - Find main branch for default operations
 * - Check for duplicate branch codes
 * ============================================================================
 */
@Repository
public interface BranchRepository extends JpaRepository<Branch, String> {

    /**
     * Find all active branches ordered by name
     * Used for branch selection dropdowns
     */
    List<Branch> findByActiveTrueOrderByNameAsc();

    /**
     * Find all branches (including inactive) for admin management
     */
    List<Branch> findAllByOrderByNameAsc();

    /**
     * Find the main/default branch for the tenant
     * Each tenant should have exactly one main branch
     */
    Optional<Branch> findByMainBranchTrue();

    /**
     * Check if a branch with the given code exists
     * Used for validation during branch creation
     */
    boolean existsByCode(String code);

    /**
     * Check if a branch with the given name exists
     * Used for validation to prevent duplicate names
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Find branch by code
     * Used for lookups by branch code
     */
    Optional<Branch> findByCode(String code);

    /**
     * Count active branches
     * Used to prevent deleting the last active branch
     */
    long countByActiveTrue();

    /**
     * Find branches that can make sales (for POS location selection)
     */
    @Query("SELECT b FROM Branch b WHERE b.active = true AND b.canSell = true ORDER BY b.name ASC")
    List<Branch> findActiveSellableBranches();
}
