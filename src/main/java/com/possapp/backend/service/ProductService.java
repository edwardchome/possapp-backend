package com.possapp.backend.service;

import com.possapp.backend.dto.CreateProductRequest;
import com.possapp.backend.dto.ProductDto;
import com.possapp.backend.entity.Category;
import com.possapp.backend.entity.Product;
import com.possapp.backend.exception.ProductException;
import com.possapp.backend.repository.CategoryRepository;
import com.possapp.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    
    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts() {
        return productRepository.findAllByActiveTrueOrderByNameAsc()
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public ProductDto getProductByCode(String code) {
        Product product = productRepository.findByCodeAndActiveTrue(code)
            .orElseThrow(() -> new ProductException("Product not found: " + code));
        return mapToDto(product);
    }
    
    @Transactional(readOnly = true)
    public Product findProductByCode(String code) {
        return productRepository.findByCodeAndActiveTrue(code)
            .orElseThrow(() -> new ProductException("Product not found: " + code));
    }
    
    @Transactional
    public ProductDto createProduct(CreateProductRequest request) {
        
        if (productRepository.existsByCode(request.getCode())) {
            throw new ProductException("Product with code already exists: " + request.getCode());
        }
        
        // Lookup category if provided
        Category category = null;
        if (request.getCategoryId() != null && !request.getCategoryId().isEmpty()) {
            category = categoryRepository.findById(request.getCategoryId())
                .orElse(null);
        }
        // Fallback to default General category if not found
        if (category == null) {
            category = categoryRepository.findByName("General")
                .orElse(null);
        }
        
        Product product = Product.builder()
            .code(request.getCode())
            .name(request.getName())
            .price(request.getPrice())
            .stock(request.getStock())
            .category(category)
            .description(request.getDescription())
            .imageUrl(request.getImageUrl())
            .costPrice(request.getCostPrice())
            .minStockLevel(request.getMinStockLevel())
            .active(true)
            .build();
        
        product = productRepository.save(product);
        log.info("Created product: {}", product.getCode());
        return mapToDto(product);
    }
    
    @Transactional
    public ProductDto updateProduct(String code, CreateProductRequest request) {
        Product product = productRepository.findByCodeAndActiveTrue(code)
            .orElseThrow(() -> new ProductException("Product not found: " + code));
        
        // Lookup category if provided
        if (request.getCategoryId() != null && !request.getCategoryId().isEmpty()) {
            Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ProductException("Category not found: " + request.getCategoryId()));
            product.setCategory(category);
        }
        
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setDescription(request.getDescription());
        product.setImageUrl(request.getImageUrl());
        product.setCostPrice(request.getCostPrice());
        product.setMinStockLevel(request.getMinStockLevel());
        
        product = productRepository.save(product);
        log.info("Updated product: {}", product.getCode());
        return mapToDto(product);
    }
    
    @Transactional
    public void deleteProduct(String code) {
        Product product = productRepository.findByCodeAndActiveTrue(code)
            .orElseThrow(() -> new ProductException("Product not found: " + code));
        product.setActive(false);
        productRepository.save(product);
        log.info("Deleted product: {}", code);
    }
    
    @Transactional
    public ProductDto updateStock(String code, int newStock) {
        if (newStock < 0) {
            throw new ProductException("Stock cannot be negative");
        }
        
        Product product = productRepository.findByCodeAndActiveTrue(code)
            .orElseThrow(() -> new ProductException("Product not found: " + code));
        
        product.setStock(newStock);
        product = productRepository.save(product);
        log.info("Updated stock for product {}: {}", code, newStock);
        return mapToDto(product);
    }
    
    @Transactional
    public ProductDto adjustStock(String code, int delta) {
        Product product = productRepository.findByCodeAndActiveTrue(code)
            .orElseThrow(() -> new ProductException("Product not found: " + code));
        
        int newStock = Math.max(0, product.getStock() + delta);
        product.setStock(newStock);
        product = productRepository.save(product);
        log.info("Adjusted stock for product {}: {} (delta: {})", code, newStock, delta);
        return mapToDto(product);
    }
    
    @Transactional(readOnly = true)
    public List<ProductDto> searchProducts(String query) {
        return productRepository.searchProducts(query)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<String> getAllCategories() {
        return productRepository.findAllCategories();
    }
    
    @Transactional(readOnly = true)
    public List<ProductDto> getProductsByCategory(String categoryId) {
        return productRepository.findByCategoryIdAndActiveTrueOrderByNameAsc(categoryId)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ProductDto> getLowStockProducts() {
        return productRepository.findByStockLessThanEqualAndActiveTrue(10)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }
    
    public ProductDto mapToDto(Product product) {
        ProductDto.ProductDtoBuilder builder = ProductDto.builder()
            .code(product.getCode())
            .name(product.getName())
            .price(product.getPrice())
            .stock(product.getStock())
            .description(product.getDescription())
            .imageUrl(product.getImageUrl())
            .costPrice(product.getCostPrice())
            .minStockLevel(product.getMinStockLevel())
            .active(product.isActive())
            .lowStock(product.isLowStock())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt());
        
        if (product.getCategory() != null) {
            builder.categoryId(product.getCategory().getId())
                   .categoryName(product.getCategory().getName());
        } else {
            builder.categoryName("General");
        }
        
        return builder.build();
    }
}
