package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.BranchDto;
import com.possapp.backend.dto.CreateBranchRequest;
import com.possapp.backend.service.BranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================================================
 * BRANCH CONTROLLER - REST API for Branch Management
 * ============================================================================
 *
 * Provides endpoints for managing store branches/locations.
 * Supports multi-location businesses where each tenant can have multiple branches.
 *
 * SECURITY:
 * - All endpoints require authentication
 * - Branch management requires ADMIN or MANAGER role
 * - Reading branch list is available to all authenticated users
 *
 * BASE PATH: /api/v1/branches
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Branch Management", description = "Manage store branches and locations")
public class BranchController {

    private final BranchService branchService;

    /**
     * ==========================================================================
     * GET ALL BRANCHES
     * ==========================================================================
     * Returns all branches (including inactive) for admin management.
     *
     * ROLE: ADMIN, MANAGER
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Get all branches", description = "Returns all branches including inactive ones")
    public ResponseEntity<ApiResponse<List<BranchDto>>> getAllBranches() {
        List<BranchDto> branches = branchService.getAllBranches();
        return ResponseEntity.ok(ApiResponse.success(branches));
    }

    /**
     * ==========================================================================
     * GET ACTIVE BRANCHES
     * ==========================================================================
     * Returns only active branches for selection dropdowns.
     *
     * ROLE: Any authenticated user
     */
    @GetMapping("/active")
    @Operation(summary = "Get active branches", description = "Returns only active branches for selection")
    public ResponseEntity<ApiResponse<List<BranchDto>>> getActiveBranches() {
        List<BranchDto> branches = branchService.getActiveBranches();
        return ResponseEntity.ok(ApiResponse.success(branches));
    }

    /**
     * ==========================================================================
     * GET ACTIVE SELLABLE BRANCHES
     * ==========================================================================
     * Returns branches that can make sales (for POS location selection).
     *
     * ROLE: Any authenticated user
     */
    @GetMapping("/sellable")
    @Operation(summary = "Get sellable branches", description = "Returns branches that can make sales")
    public ResponseEntity<ApiResponse<List<BranchDto>>> getActiveSellableBranches() {
        List<BranchDto> branches = branchService.getActiveSellableBranches();
        return ResponseEntity.ok(ApiResponse.success(branches));
    }

    /**
     * ==========================================================================
     * GET MAIN BRANCH
     * ==========================================================================
     * Returns the main/default branch for the tenant.
     *
     * ROLE: Any authenticated user
     */
    @GetMapping("/main")
    @Operation(summary = "Get main branch", description = "Returns the main/default branch")
    public ResponseEntity<ApiResponse<BranchDto>> getMainBranch() {
        BranchDto branch = branchService.getMainBranch();
        return ResponseEntity.ok(ApiResponse.success(branch));
    }

    /**
     * ==========================================================================
     * GET BRANCH BY ID
     * ==========================================================================
     *
     * ROLE: Any authenticated user
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get branch by ID", description = "Returns details of a specific branch")
    public ResponseEntity<ApiResponse<BranchDto>> getBranchById(@PathVariable String id) {
        BranchDto branch = branchService.getBranchById(id);
        return ResponseEntity.ok(ApiResponse.success(branch));
    }

    /**
     * ==========================================================================
     * CREATE BRANCH
     * ==========================================================================
     * Creates a new branch for the tenant.
     *
     * ROLE: ADMIN only
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create new branch", description = "Creates a new store branch")
    public ResponseEntity<ApiResponse<BranchDto>> createBranch(
            @Valid @RequestBody CreateBranchRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = extractUserIdFromAuthentication(authentication);
        BranchDto branch = branchService.createBranch(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Branch created successfully", branch));
    }
    
    /**
     * Extract user ID from authentication context.
     * For now, returns the username (email) as the identifier.
     * The created_by field is informational.
     */
    private String extractUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        // The principal is the username (email) from UserDetails
        return authentication.getName();
    }

    /**
     * ==========================================================================
     * UPDATE BRANCH
     * ==========================================================================
     * Updates branch details.
     *
     * ROLE: ADMIN only
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update branch", description = "Updates branch details")
    public ResponseEntity<ApiResponse<BranchDto>> updateBranch(
            @PathVariable String id,
            @Valid @RequestBody CreateBranchRequest request) {
        BranchDto branch = branchService.updateBranch(id, request);
        return ResponseEntity.ok(ApiResponse.success("Branch updated successfully", branch));
    }

    /**
     * ==========================================================================
     * RENAME BRANCH
     * ==========================================================================
     * Quick endpoint to rename a branch.
     *
     * ROLE: ADMIN only
     */
    @PatchMapping("/{id}/rename")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rename branch", description = "Quickly rename a branch")
    public ResponseEntity<ApiResponse<BranchDto>> renameBranch(
            @PathVariable String id,
            @RequestParam String name) {
        BranchDto branch = branchService.renameBranch(id, name);
        return ResponseEntity.ok(ApiResponse.success("Branch renamed successfully", branch));
    }

    /**
     * ==========================================================================
     * SET MAIN BRANCH
     * ==========================================================================
     * Changes which branch is designated as the main branch.
     *
     * ROLE: ADMIN only
     */
    @PostMapping("/{id}/set-main")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set as main branch", description = "Sets this branch as the main/default branch")
    public ResponseEntity<ApiResponse<BranchDto>> setMainBranch(@PathVariable String id) {
        BranchDto branch = branchService.setMainBranch(id);
        return ResponseEntity.ok(ApiResponse.success("Main branch updated successfully", branch));
    }

    /**
     * ==========================================================================
     * DEACTIVATE BRANCH
     * ==========================================================================
     * Soft-deletes a branch by setting active=false.
     * Cannot deactivate the main branch.
     *
     * ROLE: ADMIN only
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate branch", description = "Deactivates a branch (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deactivateBranch(@PathVariable String id) {
        branchService.deactivateBranch(id);
        return ResponseEntity.ok(ApiResponse.success("Branch deactivated successfully", null));
    }

    /**
     * ==========================================================================
     * ACTIVATE BRANCH
     * ==========================================================================
     * Reactivates a previously deactivated branch.
     *
     * ROLE: ADMIN only
     */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate branch", description = "Reactivates a deactivated branch")
    public ResponseEntity<ApiResponse<BranchDto>> activateBranch(@PathVariable String id) {
        BranchDto branch = branchService.activateBranch(id);
        return ResponseEntity.ok(ApiResponse.success("Branch activated successfully", branch));
    }
}
