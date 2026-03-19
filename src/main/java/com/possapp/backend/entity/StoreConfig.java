package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "store_name", nullable = false)
    @Builder.Default
    private String storeName = "My Store";
    
    @Column(name = "store_address")
    private String storeAddress;
    
    @Column(name = "store_phone")
    private String storePhone;
    
    @Column(name = "store_email")
    private String storeEmail;
    
    @Column(name = "receipt_header")
    private String receiptHeader;
    
    @Column(name = "receipt_footer")
    @Builder.Default
    private String receiptFooter = "Thank you for your business!";
    
    @Column(name = "tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;
    
    @Column(name = "currency_code")
    @Builder.Default
    private String currencyCode = "USD";
    
    @Column(name = "currency_symbol")
    @Builder.Default
    private String currencySymbol = "$";
    
    @Column(name = "timezone")
    @Builder.Default
    private String timezone = "UTC";
    
    @Column(name = "date_format")
    @Builder.Default
    private String dateFormat = "MMM dd, yyyy";
    
    @Column(name = "time_format")
    @Builder.Default
    private String timeFormat = "HH:mm";
    
    @Column(name = "enable_receipt_printing", nullable = false)
    @Builder.Default
    private boolean enableReceiptPrinting = true;
    
    @Column(name = "enable_email_receipts", nullable = false)
    @Builder.Default
    private boolean enableEmailReceipts = false;
    
    @Column(name = "low_stock_alert_threshold")
    @Builder.Default
    private Integer lowStockAlertThreshold = 10;
    
    @Column(name = "receipt_printer_name")
    private String receiptPrinterName;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
