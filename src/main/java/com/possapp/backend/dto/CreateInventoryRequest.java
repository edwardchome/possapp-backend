package com.possapp.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateInventoryRequest {
    
    @NotBlank(message = "Product code is required")
    private String productCode;
    
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;
    
    @Positive(message = "Unit cost must be positive")
    private BigDecimal unitCost;
    
    private String referenceNumber;
    
    private String supplierName;
    
    private String notes;
}
