package com.possapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReceiptDto {
    
    private String id;
    private LocalDateTime timestamp;
    private List<ReceiptItemDto> items;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal taxRate;
    private BigDecimal discount;
    private BigDecimal total;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String paymentMethod;
    private String paymentStatus;
    private String notes;
    private String cashierName;
    private boolean voided;
    private LocalDateTime voidedAt;
    private String voidReason;
    private LocalDateTime createdAt;
    private int itemCount;
}
