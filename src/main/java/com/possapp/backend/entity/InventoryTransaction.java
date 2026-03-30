package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "product_code", nullable = false)
    private String productCode;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_code", insertable = false, updatable = false)
    private Product product;
    
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;
    
    @Column(name = "previous_stock", nullable = false, precision = 15, scale = 4)
    private BigDecimal previousStock;
    
    @Column(name = "new_stock", nullable = false, precision = 15, scale = 4)
    private BigDecimal newStock;
    
    @Column(name = "unit_cost", precision = 10, scale = 2)
    private BigDecimal unitCost;
    
    @Column(name = "total_cost", precision = 10, scale = 2)
    private BigDecimal totalCost;
    
    @Column(name = "reference_number")
    private String referenceNumber;
    
    @Column(name = "supplier_name")
    private String supplierName;
    
    @Column(length = 1000)
    private String notes;
    
    @Column(name = "transaction_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransactionType type = TransactionType.STOCK_IN;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Branch where this inventory transaction occurred.
     * Tracks which location received/removed the stock.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;
    
    public enum TransactionType {
        STOCK_IN,      // Adding inventory
        STOCK_OUT,     // Removing inventory (adjustment)
        SALE,          // Sold to customer
        RETURN         // Customer return
    }
}
