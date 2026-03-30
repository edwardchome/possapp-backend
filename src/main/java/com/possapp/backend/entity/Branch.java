package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * ============================================================================
 * BRANCH ENTITY - Store/Branch Management for Multi-Location Businesses
 * ============================================================================
 *
 * Represents a physical location (branch/store) within a tenant's business.
 * Each tenant can have multiple branches, with one designated as the main branch.
 *
 * RELATIONSHIPS:
 * - Products, Receipts, Inventory, and Users can be associated with a branch
 * - Each tenant must have at least one branch (created automatically on registration)
 *
 * USE CASES:
 * - Chain stores with multiple locations
 * - Businesses with warehouse + retail locations
 * - Franchise management
 * - Location-specific inventory tracking
 *
 * DEFAULT BEHAVIOR:
 * - When a tenant is created, a "Main Branch" is automatically created
 * - Users can be assigned to specific branches or have access to all branches
 * - Receipts are tagged with the branch where the sale occurred
 * ============================================================================
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "branches")
public class Branch {

    /**
     * Unique identifier for the branch (UUID)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * Branch name (e.g., "Main Store", "Downtown Branch", "Warehouse")
     */
    @Column(nullable = false)
    private String name;

    /**
     * Branch code/identifier for internal reference (e.g., "MAIN", "DT001")
     */
    @Column(unique = true)
    private String code;

    /**
     * Physical address of the branch
     */
    @Column(length = 500)
    private String address;

    /**
     * Branch phone number
     */
    @Column(name = "phone_number")
    private String phoneNumber;

    /**
     * Branch email address
     */
    private String email;

    /**
     * Whether this is the main/default branch for the tenant
     * Only one branch can be the main branch per tenant
     */
    @Column(name = "is_main_branch", nullable = false)
    @Builder.Default
    private boolean mainBranch = false;

    /**
     * Whether the branch is currently active
     * Inactive branches don't appear in selection lists
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Whether this branch can make sales (false for warehouse-only locations)
     */
    @Column(name = "can_sell")
    @Builder.Default
    private boolean canSell = true;

    /**
     * Tax identification number for this specific branch (if different from main)
     */
    @Column(name = "tax_id")
    private String taxId;

    /**
     * Receipt header/footer customization for this branch
     * If null, uses the tenant's default settings
     */
    @Column(name = "receipt_header", length = 1000)
    private String receiptHeader;

    @Column(name = "receipt_footer", length = 1000)
    private String receiptFooter;

    /**
     * Branch manager name or contact person
     */
    @Column(name = "manager_name")
    private String managerName;

    /**
     * Operating hours or notes about the branch
     */
    @Column(name = "operating_hours", length = 500)
    private String operatingHours;

    /**
     * Notes or additional information about the branch
     */
    @Column(length = 1000)
    private String notes;

    /**
     * Timestamp when the branch was created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the branch was last updated
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * User ID who created this branch
     */
    @Column(name = "created_by")
    private String createdBy;
}
