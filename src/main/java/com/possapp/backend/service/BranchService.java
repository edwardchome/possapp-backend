package com.possapp.backend.service;

import com.possapp.backend.dto.BranchDto;
import com.possapp.backend.dto.CreateBranchRequest;
import com.possapp.backend.entity.Branch;
import com.possapp.backend.repository.BranchRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * BRANCH SERVICE - Business Logic for Branch Management
 * ============================================================================
 *
 * Handles all business operations related to branch/store management:
 * - Creating new branches
 * - Updating branch details
 * - Renaming branches
 * - Activating/deactivating branches
 * - Managing the main branch designation
 *
 * BUSINESS RULES:
 * 1. Every tenant must have at least one active branch
 * 2. Only one branch can be the "main" branch at a time
 * 3. The main branch cannot be deactivated
 * 4. Branch codes must be unique within the tenant
 * ============================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    /**
     * ==========================================================================
     * GET ALL BRANCHES
     * ==========================================================================
     * Returns all branches (including inactive) for admin management.
     */
    @Transactional(readOnly = true)
    public List<BranchDto> getAllBranches() {
        return branchRepository.findAllByOrderByNameAsc()
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * ==========================================================================
     * GET ACTIVE BRANCHES
     * ==========================================================================
     * Returns only active branches for selection dropdowns.
     */
    @Transactional(readOnly = true)
    public List<BranchDto> getActiveBranches() {
        return branchRepository.findByActiveTrueOrderByNameAsc()
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * ==========================================================================
     * GET ACTIVE SELLABLE BRANCHES
     * ==========================================================================
     * Returns branches that can make sales (for POS location selection).
     */
    @Transactional(readOnly = true)
    public List<BranchDto> getActiveSellableBranches() {
        return branchRepository.findActiveSellableBranches()
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * ==========================================================================
     * GET BRANCH BY ID
     * ==========================================================================
     */
    @Transactional(readOnly = true)
    public BranchDto getBranchById(String id) {
        Branch branch = branchRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Branch not found: " + id));
        return mapToDto(branch);
    }

    /**
     * ==========================================================================
     * GET MAIN BRANCH
     * ==========================================================================
     * Returns the main/default branch for the tenant.
     */
    @Transactional(readOnly = true)
    public BranchDto getMainBranch() {
        Branch branch = branchRepository.findByMainBranchTrue()
            .orElseThrow(() -> new EntityNotFoundException("Main branch not found"));
        return mapToDto(branch);
    }

    /**
     * ==========================================================================
     * CREATE BRANCH
     * ==========================================================================
     * Creates a new branch for the tenant.
     *
     * VALIDATIONS:
     * - Name is required and unique
     * - Code must be unique if provided
     */
    @Transactional
    public BranchDto createBranch(CreateBranchRequest request, String createdBy) {
        // Validate name uniqueness
        if (branchRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("Branch with this name already exists");
        }

        // Validate code uniqueness if provided
        if (request.getCode() != null && !request.getCode().isEmpty() 
                && branchRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Branch with this code already exists");
        }

        Branch branch = Branch.builder()
            .name(request.getName())
            .code(request.getCode())
            .address(request.getAddress())
            .phoneNumber(request.getPhoneNumber())
            .email(request.getEmail())
            .mainBranch(false) // New branches are never main by default
            .active(true)
            .canSell(request.isCanSell())
            .taxId(request.getTaxId())
            .receiptHeader(request.getReceiptHeader())
            .receiptFooter(request.getReceiptFooter())
            .managerName(request.getManagerName())
            .operatingHours(request.getOperatingHours())
            .notes(request.getNotes())
            .createdBy(createdBy)
            .build();

        branch = branchRepository.save(branch);
        log.info("Created new branch: {} (ID: {})", branch.getName(), branch.getId());
        
        return mapToDto(branch);
    }

    /**
     * ==========================================================================
     * UPDATE BRANCH
     * ==========================================================================
     * Updates branch details. Cannot change main branch status here.
     *
     * VALIDATIONS:
     * - Name must remain unique
     * - Code must remain unique
     */
    @Transactional
    public BranchDto updateBranch(String id, CreateBranchRequest request) {
        Branch branch = branchRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Branch not found: " + id));

        // Check name uniqueness if changed
        if (!branch.getName().equalsIgnoreCase(request.getName()) 
                && branchRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("Branch with this name already exists");
        }

        // Check code uniqueness if changed
        if (request.getCode() != null && !request.getCode().isEmpty() 
                && !request.getCode().equals(branch.getCode())
                && branchRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Branch with this code already exists");
        }

        branch.setName(request.getName());
        branch.setCode(request.getCode());
        branch.setAddress(request.getAddress());
        branch.setPhoneNumber(request.getPhoneNumber());
        branch.setEmail(request.getEmail());
        branch.setCanSell(request.isCanSell());
        branch.setTaxId(request.getTaxId());
        branch.setReceiptHeader(request.getReceiptHeader());
        branch.setReceiptFooter(request.getReceiptFooter());
        branch.setManagerName(request.getManagerName());
        branch.setOperatingHours(request.getOperatingHours());
        branch.setNotes(request.getNotes());

        branch = branchRepository.save(branch);
        log.info("Updated branch: {} (ID: {})", branch.getName(), branch.getId());
        
        return mapToDto(branch);
    }

    /**
     * ==========================================================================
     * RENAME BRANCH
     * ==========================================================================
     * Quick method to rename a branch.
     */
    @Transactional
    public BranchDto renameBranch(String id, String newName) {
        Branch branch = branchRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Branch not found: " + id));

        if (!branch.getName().equalsIgnoreCase(newName) 
                && branchRepository.existsByNameIgnoreCase(newName)) {
            throw new IllegalArgumentException("Branch with this name already exists");
        }

        String oldName = branch.getName();
        branch.setName(newName);
        branch = branchRepository.save(branch);
        
        log.info("Renamed branch from '{}' to '{}' (ID: {})", oldName, newName, id);
        return mapToDto(branch);
    }

    /**
     * ==========================================================================
     * SET MAIN BRANCH
     * ==========================================================================
     * Changes which branch is the main branch.
     * Only one branch can be main at a time.
     */
    @Transactional
    public BranchDto setMainBranch(String id) {
        Branch newMainBranch = branchRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Branch not found: " + id));

        if (!newMainBranch.isActive()) {
            throw new IllegalArgumentException("Cannot set inactive branch as main branch");
        }

        // Find and unset current main branch
        branchRepository.findByMainBranchTrue().ifPresent(currentMain -> {
            currentMain.setMainBranch(false);
            branchRepository.save(currentMain);
            log.info("Unset main branch status from: {} (ID: {})", 
                currentMain.getName(), currentMain.getId());
        });

        // Set new main branch
        newMainBranch.setMainBranch(true);
        newMainBranch = branchRepository.save(newMainBranch);
        
        log.info("Set main branch to: {} (ID: {})", newMainBranch.getName(), newMainBranch.getId());
        return mapToDto(newMainBranch);
    }

    /**
     * ==========================================================================
     * DEACTIVATE BRANCH
     * ==========================================================================
     * Soft-deletes a branch by setting active=false.
     *
     * RESTRICTIONS:
     * - Cannot deactivate the main branch
     * - Must have at least one active branch remaining
     */
    @Transactional
    public void deactivateBranch(String id) {
        Branch branch = branchRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Branch not found: " + id));

        // Cannot deactivate main branch
        if (branch.isMainBranch()) {
            throw new IllegalArgumentException("Cannot deactivate the main branch. Set another branch as main first.");
        }

        // Check if this is the last active branch
        long activeCount = branchRepository.countByActiveTrue();
        if (activeCount <= 1) {
            throw new IllegalArgumentException("Cannot deactivate the last active branch");
        }

        branch.setActive(false);
        branchRepository.save(branch);
        log.info("Deactivated branch: {} (ID: {})", branch.getName(), id);
    }

    /**
     * ==========================================================================
     * ACTIVATE BRANCH
     * ==========================================================================
     * Reactivates a previously deactivated branch.
     */
    @Transactional
    public BranchDto activateBranch(String id) {
        Branch branch = branchRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Branch not found: " + id));

        branch.setActive(true);
        branch = branchRepository.save(branch);
        log.info("Activated branch: {} (ID: {})", branch.getName(), id);
        
        return mapToDto(branch);
    }

    /**
     * ==========================================================================
     * MAP ENTITY TO DTO
     * ==========================================================================
     */
    private BranchDto mapToDto(Branch branch) {
        return BranchDto.builder()
            .id(branch.getId())
            .name(branch.getName())
            .code(branch.getCode())
            .address(branch.getAddress())
            .phoneNumber(branch.getPhoneNumber())
            .email(branch.getEmail())
            .mainBranch(branch.isMainBranch())
            .active(branch.isActive())
            .canSell(branch.isCanSell())
            .taxId(branch.getTaxId())
            .receiptHeader(branch.getReceiptHeader())
            .receiptFooter(branch.getReceiptFooter())
            .managerName(branch.getManagerName())
            .operatingHours(branch.getOperatingHours())
            .notes(branch.getNotes())
            .createdAt(branch.getCreatedAt())
            .updatedAt(branch.getUpdatedAt())
            .createdBy(branch.getCreatedBy())
            .build();
    }
}
