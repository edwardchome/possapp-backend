package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.CategoryDto;
import com.possapp.backend.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Product category management APIs")
public class CategoryController {
    
    private final CategoryService categoryService;
    
    @GetMapping
    @Operation(
        summary = "Get all categories",
        description = "Get all categories ordered by display order. Use includeInactive=true to include inactive categories."
    )
    public ResponseEntity<ApiResponse<List<CategoryDto>>> getAllCategories(
            @Parameter(description = "Include inactive categories")
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        List<CategoryDto> categories = categoryService.getAllCategories(includeInactive);
        return ResponseEntity.ok(ApiResponse.success(categories));
    }
    
    @GetMapping("/{id}")
    @Operation(
        summary = "Get category by ID",
        description = "Get a specific category by its ID"
    )
    public ResponseEntity<ApiResponse<CategoryDto>> getCategoryById(
            @Parameter(description = "Category ID", required = true)
            @PathVariable String id) {
        CategoryDto category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.success(category));
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_MANAGER')")
    @Operation(
        summary = "Create category",
        description = "Create a new product category (Admin/Manager only)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(
            @Parameter(description = "Category details", required = true)
            @Valid @RequestBody CategoryDto dto) {
        CategoryDto created = categoryService.createCategory(dto);
        return ResponseEntity.ok(ApiResponse.success("Category created successfully", created));
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_MANAGER')")
    @Operation(
        summary = "Update category",
        description = "Update an existing category (Admin/Manager only)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<CategoryDto>> updateCategory(
            @Parameter(description = "Category ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Updated category details", required = true)
            @Valid @RequestBody CategoryDto dto) {
        CategoryDto updated = categoryService.updateCategory(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", updated));
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_MANAGER')")
    @Operation(
        summary = "Delete category",
        description = "Deactivate a category (Admin/Manager only)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @Parameter(description = "Category ID", required = true)
            @PathVariable String id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }
    
    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_MANAGER')")
    @Operation(
        summary = "Reactivate category",
        description = "Reactivate a deactivated category (Admin/Manager only)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<CategoryDto>> reactivateCategory(
            @Parameter(description = "Category ID", required = true)
            @PathVariable String id) {
        CategoryDto reactivated = categoryService.reactivateCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category reactivated successfully", reactivated));
    }
}
