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

@Slf4j
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Management", description = "Tenant registration and management APIs")
public class TenantController {
    
    private final TenantService tenantService;
    
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
        
        TenantDto tenant = tenantService.registerTenant(request);
        return ResponseEntity.ok(ApiResponse.success("Tenant registered successfully", tenant));
    }
    
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
