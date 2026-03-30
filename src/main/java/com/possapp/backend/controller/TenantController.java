package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.TenantDto;
import com.possapp.backend.dto.TenantRegistrationRequest;
import com.possapp.backend.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * ============================================================================
 * TENANT CONTROLLER - Business/Store Management API
 * ============================================================================
 * 
 * This controller handles all tenant (business/store) related operations.
 * In this multi-tenant system, each business is a "tenant" with its own 
 * database schema. This keeps each business's data completely separate.
 * 
 * FLOW:
 * 1. User registers a new business -> Creates tenant + database schema
 * 2. Admin can view/update business settings
 * 3. Each request includes X-Tenant-ID header to identify which business
 * 
 * BASE URL: /api/v1/tenants
 * ============================================================================
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Management", description = "Tenant registration and management APIs")
public class TenantController {
    
    /**
     * TenantService contains all business logic for tenant operations.
     * It handles database schema creation, user setup, etc.
     */
    private final TenantService tenantService;
    
    /**
     * ==========================================================================
     * REGISTER NEW TENANT (Business Registration)
     * ==========================================================================
     * Called when a new user creates a business account during onboarding.
     * 
     * FLOW:
     * 1. User fills business name, schema name, admin email, password
     * 2. Backend validates the data
     * 3. Creates a new database schema for this business
     * 4. Creates tables (users, products, receipts, etc.) in that schema
     * 5. Creates admin user with the provided email/password
     * 6. Returns the newly created tenant details
     * 
     * ENDPOINT: POST /api/v1/tenants/register
     * AUTH: None (public endpoint for registration)
     * ==========================================================================
     */
    @PostMapping("/register")
    @Operation(
        summary = "Register new tenant",
        description = "Create a new tenant with schema, admin user, and initial setup"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tenant registered successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or tenant already exists")
    })
    public ResponseEntity<ApiResponse<TenantDto>> registerTenant(
            @Parameter(description = "Tenant registration details", required = true)
            @Valid @RequestBody TenantRegistrationRequest request) {
        log.info("Tenant registration request: {}", request.getCompanyName());
        
        // Delegate to service which handles the complex setup process
        TenantDto tenant = tenantService.registerTenant(request);
        return ResponseEntity.ok(ApiResponse.success("Tenant registered successfully", tenant));
    }
    
    /**
     * ==========================================================================
     * GET TENANT BY SCHEMA NAME
     * ==========================================================================
     * Retrieves tenant details by its schema name (business ID).
     * Used for looking up business information.
     * 
     * ENDPOINT: GET /api/v1/tenants/{schemaName}
     * AUTH: Requires JWT token (bearerAuth)
     * ==========================================================================
     */
    @GetMapping("/{schemaName}")
    @Operation(
        summary = "Get tenant by schema name",
        description = "Retrieve tenant details by schema name",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tenant found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<ApiResponse<TenantDto>> getTenant(
            @Parameter(description = "Tenant schema name", required = true, example = "acme_corp")
            @PathVariable String schemaName) {
        return tenantService.findBySchemaName(schemaName)
            .map(tenant -> ResponseEntity.ok(ApiResponse.success(tenantService.mapToDto(tenant))))
            .orElse(ResponseEntity.status(404)
                .body(ApiResponse.<TenantDto>error("Tenant not found")));
    }
    
    /**
     * ==========================================================================
     * DEACTIVATE TENANT
     * ==========================================================================
     * Soft-deletes a tenant by setting active=false.
     * The data remains but the business cannot operate.
     * 
     * ENDPOINT: POST /api/v1/tenants/{tenantId}/deactivate
     * AUTH: Requires admin JWT token
     * ==========================================================================
     */
    @PostMapping("/{tenantId}/deactivate")
    @Operation(
        summary = "Deactivate tenant",
        description = "Deactivate a tenant (admin only)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> deactivateTenant(
            @Parameter(description = "Tenant ID (UUID)", required = true)
            @PathVariable UUID tenantId) {
        tenantService.deactivateTenant(tenantId.toString());
        return ResponseEntity.ok(ApiResponse.success("Tenant deactivated", null));
    }
    
    /**
     * ==========================================================================
     * ACTIVATE TENANT
     * ==========================================================================
     * Re-activates a previously deactivated tenant.
     * 
     * ENDPOINT: POST /api/v1/tenants/{tenantId}/activate
     * AUTH: Requires admin JWT token
     * ==========================================================================
     */
    @PostMapping("/{tenantId}/activate")
    @Operation(
        summary = "Activate tenant",
        description = "Activate a previously deactivated tenant (admin only)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> activateTenant(
            @Parameter(description = "Tenant ID (UUID)", required = true)
            @PathVariable UUID tenantId) {
        tenantService.activateTenant(tenantId.toString());
        return ResponseEntity.ok(ApiResponse.success("Tenant activated", null));
    }
    
    /**
     * ==========================================================================
     * GET CURRENT TENANT (Business Settings Page)
     * ==========================================================================
     * Returns the current tenant's details based on the X-Tenant-ID header
     * in the request. This is used in the mobile app's Business Settings screen.
     * 
     * FLOW:
     * 1. Mobile app sends request with X-Tenant-ID header
     * 2. TenantFilter extracts this and sets TenantContext
     * 3. This method gets the tenant from TenantContext
     * 4. Returns business info (name, phone, address, etc.)
     * 
     * ENDPOINT: GET /api/v1/tenants/current
     * AUTH: Requires JWT token
     * ==========================================================================
     */
    @GetMapping("/current")
    @Operation(
        summary = "Get current tenant",
        description = "Get current tenant details based on X-Tenant-ID header",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<TenantDto>> getCurrentTenant() {
        TenantDto tenant = tenantService.getCurrentTenant();
        if (tenant == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.<TenantDto>error("Tenant not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }
    
    /**
     * ==========================================================================
     * UPDATE CURRENT TENANT (Save Business Settings)
     * ==========================================================================
     * Updates the current tenant's business information.
     * Called when user saves changes in Business Settings screen.
     * 
     * UPDATABLE FIELDS:
     * - companyName: Business name shown on receipts
     * - contactPhone: Business phone number
     * - address: Business address shown on receipts
     * 
     * NON-UPDATABLE (read-only):
     * - schemaName: The business ID (used for login)
     * - adminEmail: Contact support to change
     * 
     * ENDPOINT: PUT /api/v1/tenants/current
     * AUTH: Requires JWT token
     * ==========================================================================
     */
    @PutMapping("/current")
    @Operation(
        summary = "Update current tenant",
        description = "Update current tenant business settings",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<TenantDto>> updateCurrentTenant(
            @Valid @RequestBody TenantDto tenantDto) {
        TenantDto updated = tenantService.updateCurrentTenant(tenantDto);
        return ResponseEntity.ok(ApiResponse.success("Business settings updated", updated));
    }
}
