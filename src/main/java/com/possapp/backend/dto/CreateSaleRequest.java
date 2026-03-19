package com.possapp.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateSaleRequest {
    
    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<SaleItemRequest> items;
    
    @PositiveOrZero(message = "Discount cannot be negative")
    private BigDecimal discount = BigDecimal.ZERO;
    
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String paymentMethod = "Cash";
    private String notes;
    
    @Data
    public static class SaleItemRequest {
        @NotBlank(message = "Product code is required")
        private String code;
        
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        private Integer quantity;
    }
}
