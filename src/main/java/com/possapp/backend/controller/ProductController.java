package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.CreateProductRequest;
import com.possapp.backend.dto.ProductDto;
import com.possapp.backend.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management APIs")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {
    
    private final ProductService productService;
    
    @GetMapping
    @Operation(
        summary = "Get all products",
        description = "Retrieve all products in the current tenant"
    )
    public ResponseEntity<ApiResponse<List<ProductDto>>> getAllProducts() {
        List<ProductDto> products = productService.getAllProducts();
        return ResponseEntity.ok(ApiResponse.success(products));
    }
    
    @GetMapping("/{code}")
    @Operation(
        summary = "Get product by code",
        description = "Retrieve a specific product by its code"
    )
    public ResponseEntity<ApiResponse<ProductDto>> getProductByCode(
            @Parameter(description = "Product code/SKU", required = true, example = "PROD001")
            @PathVariable String code) {
        ProductDto product = productService.getProductByCode(code);
        return ResponseEntity.ok(ApiResponse.success(product));
    }
    
    @PostMapping
    @Operation(
        summary = "Create product",
        description = "Create a new product"
    )
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(
            @Parameter(description = "Product details", required = true)
            @Valid @RequestBody CreateProductRequest request) {
        log.info("Creating product: {}", request.getCode());
        ProductDto product = productService.createProduct(request);
        return ResponseEntity.ok(ApiResponse.success("Product created", product));
    }
    
    @PutMapping("/{code}")
    @Operation(
        summary = "Update product",
        description = "Update an existing product"
    )
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @Parameter(description = "Product code/SKU", required = true)
            @PathVariable String code,
            @Parameter(description = "Updated product details", required = true)
            @Valid @RequestBody CreateProductRequest request) {
        log.info("Updating product: {}", code);
        ProductDto product = productService.updateProduct(code, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated", product));
    }
    
    @DeleteMapping("/{code}")
    @Operation(
        summary = "Delete product",
        description = "Delete a product by code"
    )
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @Parameter(description = "Product code/SKU", required = true)
            @PathVariable String code) {
        log.info("Deleting product: {}", code);
        productService.deleteProduct(code);
        return ResponseEntity.ok(ApiResponse.success("Product deleted", null));
    }
    
    @PatchMapping("/{code}/stock")
    @Operation(
        summary = "Update stock",
        description = "Set product stock to a specific value"
    )
    public ResponseEntity<ApiResponse<ProductDto>> updateStock(
            @Parameter(description = "Product code/SKU", required = true)
            @PathVariable String code,
            @Parameter(description = "Stock update request", required = true)
            @RequestBody Map<String, BigDecimal> request) {
        BigDecimal newStock = request.get("stock");
        ProductDto product = productService.updateStock(code, newStock);
        return ResponseEntity.ok(ApiResponse.success("Stock updated", product));
    }
    
    @PostMapping("/{code}/stock/adjust")
    @Operation(
        summary = "Adjust stock",
        description = "Adjust product stock by a delta value (positive or negative)"
    )
    public ResponseEntity<ApiResponse<ProductDto>> adjustStock(
            @Parameter(description = "Product code/SKU", required = true)
            @PathVariable String code,
            @Parameter(description = "Stock adjustment request", required = true)
            @RequestBody Map<String, BigDecimal> request) {
        BigDecimal delta = request.get("delta");
        ProductDto product = productService.adjustStock(code, delta);
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted", product));
    }
    
    @GetMapping("/search")
    @Operation(
        summary = "Search products",
        description = "Search products by name or description"
    )
    public ResponseEntity<ApiResponse<List<ProductDto>>> searchProducts(
            @Parameter(description = "Search query", required = true, example = "coffee")
            @RequestParam String query) {
        List<ProductDto> products = productService.searchProducts(query);
        return ResponseEntity.ok(ApiResponse.success(products));
    }
    
    @GetMapping("/categories")
    @Operation(
        summary = "Get categories",
        description = "Get all unique product categories"
    )
    public ResponseEntity<ApiResponse<List<String>>> getAllCategories() {
        List<String> categories = productService.getAllCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }
    
    @GetMapping("/low-stock")
    @Operation(
        summary = "Get low stock products",
        description = "Get products with stock below minimum threshold"
    )
    public ResponseEntity<ApiResponse<List<ProductDto>>> getLowStockProducts() {
        List<ProductDto> products = productService.getLowStockProducts();
        return ResponseEntity.ok(ApiResponse.success(products));
    }
}
