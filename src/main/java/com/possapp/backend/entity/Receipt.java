package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receipt {
    
    @Id
    private String id; // Custom ID format: RCP-{timestamp}
    
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ReceiptItem> items = new ArrayList<>();
    
    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
    
    @Column(name = "tax", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;
    
    @Column(name = "tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;
    
    @Column(name = "discount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;
    
    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;
    
    @Column(name = "customer_name")
    private String customerName;
    
    @Column(name = "customer_email")
    private String customerEmail;
    
    @Column(name = "customer_phone")
    private String customerPhone;
    
    @Column(name = "payment_method", nullable = false)
    @Builder.Default
    private String paymentMethod = "Cash";
    
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private String paymentStatus = "PAID"; // PAID, PENDING, REFUNDED
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "cashier_id")
    private String cashierId;
    
    @Column(name = "cashier_name")
    private String cashierName;
    
    @Column(name = "is_voided", nullable = false)
    @Builder.Default
    private boolean voided = false;
    
    @Column(name = "voided_at")
    private LocalDateTime voidedAt;
    
    @Column(name = "void_reason")
    private String voidReason;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Helper method to add item
    public void addItem(ReceiptItem item) {
        items.add(item);
        item.setReceipt(this);
    }
    
    // Helper method to remove item
    public void removeItem(ReceiptItem item) {
        items.remove(item);
        item.setReceipt(null);
    }
    
    // Calculate totals
    public void calculateTotals() {
        this.subtotal = items.stream()
            .map(ReceiptItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.tax = subtotal.multiply(taxRate).divide(BigDecimal.valueOf(100));
        this.total = subtotal.add(tax).subtract(discount);
    }
    
    // Void receipt
    public void voidReceipt(String reason) {
        this.voided = true;
        this.voidedAt = LocalDateTime.now();
        this.voidReason = reason;
    }
}
