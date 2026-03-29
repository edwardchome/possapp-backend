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
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/units")
@RequiredArgsConstructor
@Tag(name = "Units of Measure", description = "Manage units of measurement for products")
public class UnitOfMeasureController {
    
    private final UnitOfMeasureRepository unitRepository;
    
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
    
    @GetMapping("/{id}")
    @Operation(summary = "Get unit by ID", description = "Get a specific unit of measure")
    public ResponseEntity<ApiResponse<UnitOfMeasure>> getUnitById(@PathVariable String id) {
        return unitRepository.findById(id)
                .map(unit -> ResponseEntity.ok(ApiResponse.success(unit)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/by-type/{type}")
    @Operation(summary = "Get units by type", description = "Get units filtered by type (COUNT, WEIGHT, VOLUME, LENGTH, AREA)")
    public ResponseEntity<ApiResponse<List<UnitOfMeasure>>> getUnitsByType(
            @PathVariable UnitOfMeasure.UnitType type) {
        List<UnitOfMeasure> units = unitRepository.findByTypeAndActiveTrue(type);
        return ResponseEntity.ok(ApiResponse.success(units));
    }
    
    @PostMapping
    @Operation(summary = "Create unit", description = "Create a new unit of measure")
    public ResponseEntity<ApiResponse<UnitOfMeasure>> createUnit(@RequestBody UnitOfMeasure unit) {
        if (unitRepository.existsBySymbol(unit.getSymbol())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Unit with symbol '" + unit.getSymbol() + "' already exists"));
        }
        
        UnitOfMeasure saved = unitRepository.save(unit);
        return ResponseEntity.ok(ApiResponse.success("Unit created successfully", saved));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update unit", description = "Update an existing unit of measure")
    public ResponseEntity<ApiResponse<UnitOfMeasure>> updateUnit(
            @PathVariable String id,
            @RequestBody UnitOfMeasure unit) {
        return unitRepository.findById(id)
                .map(existing -> {
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
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete unit", description = "Soft delete a unit of measure")
    public ResponseEntity<ApiResponse<Void>> deleteUnit(@PathVariable String id) {
        Optional<UnitOfMeasure> unitOpt = unitRepository.findById(id);
        if (unitOpt.isPresent()) {
            UnitOfMeasure unit = unitOpt.get();
            unit.setActive(false);
            unitRepository.save(unit);
            return ResponseEntity.ok(ApiResponse.success("Unit deleted successfully", null));
        }
        return ResponseEntity.notFound().build();
    }
    
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
