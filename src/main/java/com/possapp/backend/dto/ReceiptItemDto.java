package com.possapp.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptItemDto {
    
    private String id;
    private String productCode;
    private String productName;
    private BigDecimal price;
    private Integer qty;
    private BigDecimal lineTotal;
}
