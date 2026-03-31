package com.possapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDto {
    
    private String code;
    private String name;
    private BigDecimal price;
    private BigDecimal sellingPrice;
    private BigDecimal stock;
    private String categoryId;
    private String categoryName;
    private String description;
    private String imageUrl;
    private BigDecimal costPrice;
    private BigDecimal minStockLevel;
    private boolean active;
    private boolean lowStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Unit of measurement fields
    private String unitOfMeasure;
    private Boolean allowDecimal;
    private BigDecimal minQuantity;
    private BigDecimal stepQuantity;
    
    // Branch assignment
    private BranchDto branch;
}
