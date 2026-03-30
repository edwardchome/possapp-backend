package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.entity.UnitOfMeasure;
import com.possapp.backend.repository.UnitOfMeasureRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================================================
 * UNIT OF MEASURE CONTROLLER - Product Measurement Units API
 * ============================================================================
 * 
 * This controller manages measurement units for products (kg, liters, pcs, etc.)
 * Each business (tenant) can have their own custom units of measurement.
 * 
 * USE CASES:
 * 1. Grocery stores: kg, g, liters, ml for food items
 * 2. Hardware stores: meters, cm, m² for building materials
 * 3. General stores: pieces (pcs), boxes, packets
 * 
 * FEATURES:
 * - Active/Inactive units (soft delete)
 * - Fractional quantities support (1.5 kg, 0.5 liters)
 * - Decimal precision configuration (0.001 kg = 3 decimals)
 * 
 * BASE URL: /api/v1/units
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1/units")
@RequiredArgsConstructor
@Tag(name = "Units of Measure", description = "Manage units of measurement for products")
public class UnitOfMeasureController {
    
    /**
     * Repository for UnitOfMeasure database operations.
     * Automatically handles multi-tenancy via schema filtering.
     */
    private final UnitOfMeasureRepository unitRepository;
    
    /**
     * ==========================================================================
     * GET ACTIVE UNITS ONLY
     * ==========================================================================
     * Returns only active units. Used in product forms (add/edit product)
     * where users select a unit for their products.
     * 
     * FALLBACK: If no units configured, returns system default units
     * (pcs, kg, g, LT, ml, m, cm, m²)
     * 
     * ENDPOINT: GET /api/v1/units
     * AUTH: Requires JWT token
     * MOBILE USAGE: AddProductScreen, EditProductScreen dropdowns
     * ==========================================================================
     */
    @GetMapping
    @Operation(summary = "Get all units", description = "Get all active units of measure")
    public ResponseEntity<ApiResponse<List<UnitOfMeasure>>> getAllUnits() {
        List<UnitOfMeasure> units = unitRepository.findByActiveTrue();
        
        // Return default units if none configured
        if (units.isEmpty()) {
            units = getDefaultUnits();
        }
        
        return ResponseEntity.ok(ApiResponse.success(units));
    }
    
    /**
     * ==========================================================================
     * GET ALL UNITS (Including Inactive) - Admin Only
     * ==========================================================================
     * Returns ALL units including inactive ones. Used in Unit Management
     * screen where admins can see and restore deactivated units.
     * 
     * ENDPOINT: GET /api/v1/units/all
     * AUTH: Requires admin/manager JWT token
     * MOBILE USAGE: UnitManagementScreen (settings page)
     * ==========================================================================
     */
    @GetMapping("/all")
    @Operation(summary = "Get all units including inactive", description = "Get all units including inactive (for admin)")
    public ResponseEntity<ApiResponse<List<UnitOfMeasure>>> getAllUnitsIncludingInactive() {
        List<UnitOfMeasure> units = unitRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(units));
    }
    
    /**
     * ==========================================================================
     * GET UNIT BY ID
     * ==========================================================================
     * Retrieves a specific unit by its ID.
     * 
     * ENDPOINT: GET /api/v1/units/{id}
     * AUTH: Requires JWT token
     * ==========================================================================
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get unit by ID", description = "Get a specific unit of measure")
    public ResponseEntity<ApiResponse<UnitOfMeasure>> getUnitById(@PathVariable String id) {
        return unitRepository.findById(id)
                .map(unit -> ResponseEntity.ok(ApiResponse.success(unit)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * ==========================================================================
     * GET UNITS BY TYPE
     * ==========================================================================
     * Filters units by their type (COUNT, WEIGHT, VOLUME, LENGTH, AREA).
     * Useful for grouping units in the UI.
     * 
     * EXAMPLE: GET /api/v1/units/by-type/WEIGHT returns [kg, g]
     * 
     * ENDPOINT: GET /api/v1/units/by-type/{type}
     * AUTH: Requires JWT token
     * ==========================================================================
     */
    @GetMapping("/by-type/{type}")
    @Operation(summary = "Get units by type", description = "Get units filtered by type (COUNT, WEIGHT, VOLUME, LENGTH, AREA)")
    public ResponseEntity<ApiResponse<List<UnitOfMeasure>>> getUnitsByType(
            @PathVariable UnitOfMeasure.UnitType type) {
        List<UnitOfMeasure> units = unitRepository.findByTypeAndActiveTrue(type);
        return ResponseEntity.ok(ApiResponse.success(units));
    }
    
    /**
     * ==========================================================================
     * CREATE NEW UNIT
     * ==========================================================================
     * Creates a custom unit of measure for the business.
     * 
     * EXAMPLE REQUEST:
     * {
     *   "name": "Box",
     *   "symbol": "box",
     *   "type": "COUNT",
     *   "allowFractions": false,
     *   "defaultPrecision": 0
     * }
     * 
     * VALIDATION: Symbol must be unique within the tenant
     * 
     * ENDPOINT: POST /api/v1/units
     * AUTH: Requires admin/manager JWT token
     * MOBILE USAGE: UnitManagementScreen - "Add Unit" dialog
     * ==========================================================================
     */
    @PostMapping
    @Operation(summary = "Create unit", description = "Create a new unit of measure")
    public ResponseEntity<ApiResponse<UnitOfMeasure>> createUnit(@RequestBody UnitOfMeasure unit) {
        // Check for duplicate symbol
        if (unitRepository.existsBySymbol(unit.getSymbol())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Unit with symbol '" + unit.getSymbol() + "' already exists"));
        }
        
        UnitOfMeasure saved = unitRepository.save(unit);
        return ResponseEntity.ok(ApiResponse.success("Unit created successfully", saved));
    }
    
    /**
     * ==========================================================================
     * UPDATE UNIT
     * ==========================================================================
     * Updates an existing unit's properties.
     * 
     * UPDATABLE FIELDS:
     * - name, symbol, type, description
     * - allowFractions: Whether to allow decimal quantities
     * - defaultPrecision: Number of decimal places (0-4)
     * - active: Can reactivate a deactivated unit
     * 
     * ENDPOINT: PUT /api/v1/units/{id}
     * AUTH: Requires admin/manager JWT token
     * MOBILE USAGE: UnitManagementScreen - "Edit Unit" dialog
     * ==========================================================================
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update unit", description = "Update an existing unit of measure")
    public ResponseEntity<ApiResponse<UnitOfMeasure>> updateUnit(
            @PathVariable String id,
            @RequestBody UnitOfMeasure unit) {
        return unitRepository.findById(id)
            .map(existing -> {
                // Update all properties
                existing.setName(unit.getName());
                existing.setSymbol(unit.getSymbol());
                existing.setType(unit.getType());
                existing.setDescription(unit.getDescription());
                existing.setAllowFractions(unit.isAllowFractions());
                existing.setDefaultPrecision(unit.getDefaultPrecision());
                existing.setActive(unit.isActive());
                
                UnitOfMeasure saved = unitRepository.save(existing);
                return ResponseEntity.ok(ApiResponse.success("Unit updated successfully", saved));
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * ==========================================================================
     * DELETE UNIT (Soft Delete)
     * ==========================================================================
     * Deactivates a unit by setting active=false.
     * This is a "soft delete" - the unit remains in database but won't
     * appear in product forms. Can be restored later.
     * 
     * WHY SOFT DELETE?
     * - Existing products using this unit keep working
     * - Can be restored if deleted by mistake
     * - Historical records remain intact
     * 
     * ENDPOINT: DELETE /api/v1/units/{id}
     * AUTH: Requires admin/manager JWT token
     * MOBILE USAGE: UnitManagementScreen - "Deactivate" button
     * ==========================================================================
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete unit", description = "Soft delete a unit of measure")
    public ResponseEntity<ApiResponse<Void>> deleteUnit(@PathVariable String id) {
        java.util.Optional<UnitOfMeasure> unitOpt = unitRepository.findById(id);
        if (unitOpt.isPresent()) {
            UnitOfMeasure unit = unitOpt.get();
            unit.setActive(false); // Soft delete - just mark as inactive
            unitRepository.save(unit);
            return ResponseEntity.ok(ApiResponse.success("Unit deleted successfully", null));
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * ==========================================================================
     * RESTORE UNIT (Reactivate)
     * ==========================================================================
     * Reactivates a previously deactivated unit.
     * The unit will again appear in product forms.
     * 
     * ENDPOINT: POST /api/v1/units/{id}/restore
     * AUTH: Requires admin/manager JWT token
     * MOBILE USAGE: UnitManagementScreen - "Reactivate" button
     * ==========================================================================
     */
    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore unit", description = "Restore (reactivate) a unit of measure")
    public ResponseEntity<ApiResponse<UnitOfMeasure>> restoreUnit(@PathVariable String id) {
        java.util.Optional<UnitOfMeasure> unitOpt = unitRepository.findById(id);
        if (unitOpt.isPresent()) {
            UnitOfMeasure unit = unitOpt.get();
            unit.setActive(true);
            UnitOfMeasure saved = unitRepository.save(unit);
            return ResponseEntity.ok(ApiResponse.success("Unit restored successfully", saved));
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * ==========================================================================
     * DEFAULT UNITS
     * ==========================================================================
     * Returns the system default units. These are used when a new tenant
     * is created and hasn't configured custom units yet.
     * 
     * DEFAULT UNITS:
     * - Pieces (pcs): Countable items
     * - Kilogram (kg): Weight, 3 decimal precision
     * - Gram (g): Small weight, whole numbers
     * - Liter (LT): Volume, 2 decimal precision
     * - Milliliter (ml): Small volume
     * - Meter (m): Length
     * - Centimeter (cm): Small length
     * - Square Meter (m²): Area measurement
     * ==========================================================================
     */
    private List<UnitOfMeasure> getDefaultUnits() {
        return List.of(
            UnitOfMeasure.builder()
                    .name("Pieces")
                    .symbol("pcs")
                    .type(UnitOfMeasure.UnitType.COUNT)
                    .description("Individual items")
                    .allowFractions(false)
                    .defaultPrecision(0)
                    .active(true)
                    .build(),
            UnitOfMeasure.builder()
                    .name("Kilogram")
                    .symbol("kg")
                    .type(UnitOfMeasure.UnitType.WEIGHT)
                    .description("Weight in kilograms")
                    .allowFractions(true)
                    .defaultPrecision(3)
                    .active(true)
                    .build(),
            UnitOfMeasure.builder()
                    .name("Gram")
                    .symbol("g")
                    .type(UnitOfMeasure.UnitType.WEIGHT)
                    .description("Weight in grams")
                    .allowFractions(true)
                    .defaultPrecision(0)
                    .active(true)
                    .build(),
            UnitOfMeasure.builder()
                    .name("Liter")
                    .symbol("LT")
                    .type(UnitOfMeasure.UnitType.VOLUME)
                    .description("Volume in liters")
                    .allowFractions(true)
                    .defaultPrecision(2)
                    .active(true)
                    .build(),
            UnitOfMeasure.builder()
                    .name("Milliliter")
                    .symbol("ml")
                    .type(UnitOfMeasure.UnitType.VOLUME)
                    .description("Volume in milliliters")
                    .allowFractions(true)
                    .defaultPrecision(0)
                    .active(true)
                    .build(),
            UnitOfMeasure.builder()
                    .name("Meter")
                    .symbol("m")
                    .type(UnitOfMeasure.UnitType.LENGTH)
                    .description("Length in meters")
                    .allowFractions(true)
                    .defaultPrecision(2)
                    .active(true)
                    .build(),
            UnitOfMeasure.builder()
                    .name("Centimeter")
                    .symbol("cm")
                    .type(UnitOfMeasure.UnitType.LENGTH)
                    .description("Length in centimeters")
                    .allowFractions(true)
                    .defaultPrecision(1)
                    .active(true)
                    .build(),
            UnitOfMeasure.builder()
                    .name("Square Meter")
                    .symbol("m²")
                    .type(UnitOfMeasure.UnitType.AREA)
                    .description("Area in square meters")
                    .allowFractions(true)
                    .defaultPrecision(2)
                    .active(true)
                    .build()
        );
    }
}
