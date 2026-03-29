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
public class InventoryTransactionDto {
    
    private String id;
    private String productCode;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal previousStock;
    private BigDecimal newStock;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private String referenceNumber;
    private String supplierName;
    private String notes;
    private String type;
    private String createdBy;
    private LocalDateTime createdAt;
}
