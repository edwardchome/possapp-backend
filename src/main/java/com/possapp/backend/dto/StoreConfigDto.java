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
public class StoreConfigDto {
    
    private Long id;
    private String storeName;
    private String storeAddress;
    private String storePhone;
    private String storeEmail;
    private String receiptHeader;
    private String receiptFooter;
    private BigDecimal taxRate;
    private String currencyCode;
    private String currencySymbol;
    private String timezone;
    private String dateFormat;
    private String timeFormat;
    private boolean enableReceiptPrinting;
    private boolean enableEmailReceipts;
    private Integer lowStockAlertThreshold;
    private LocalDateTime updatedAt;
}
