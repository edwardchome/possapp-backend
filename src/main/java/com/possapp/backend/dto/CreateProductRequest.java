package com.possapp.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProductRequest {
    
    @NotBlank(message = "Product code is required")
    private String code;
    
    @NotBlank(message = "Product name is required")
    private String name;
    
    @NotNull(message = "Price is required")
    @PositiveOrZero(message = "Price must be zero or positive")
    private BigDecimal price;
    
    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stock;
    
    private String categoryId;
    private String description;
    private String imageUrl;
    
    @PositiveOrZero(message = "Cost price must be zero or positive")
    private BigDecimal costPrice;
    
    @Min(value = 0, message = "Min stock level cannot be negative")
    private Integer minStockLevel = 0;
}
