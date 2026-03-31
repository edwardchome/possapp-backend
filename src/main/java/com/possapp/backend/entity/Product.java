package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    
    @Id
    private String code; // Barcode/QR code as primary key
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(name = "selling_price", precision = 10, scale = 2)
    private BigDecimal sellingPrice;
    
    @Column(nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal stock = BigDecimal.ZERO;
    
    @Column(name = "unit_of_measure", length = 20)
    @Builder.Default
    private String unitOfMeasure = "PCS"; // Default: pieces
    
    @Column(name = "allow_decimal", nullable = false)
    @Builder.Default
    private Boolean allowDecimal = false;
    
    @Column(name = "min_quantity", precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal minQuantity = BigDecimal.ONE;
    
    @Column(name = "step_quantity", precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal stepQuantity = BigDecimal.ONE;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;
    
    @Column(length = 2000)
    private String description;
    
    @Column(name = "image_url")
    private String imageUrl;
    
    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;
    
    @Column(name = "min_stock_level", precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal minStockLevel = BigDecimal.ZERO;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private String createdBy;
    
    // Helper method to check if stock is low
    public boolean isLowStock() {
        return stock.compareTo(minStockLevel) <= 0;
    }
    
    // Helper method to adjust stock
    public void adjustStock(BigDecimal delta) {
        this.stock = this.stock.add(delta);
        if (this.stock.compareTo(BigDecimal.ZERO) < 0) {
            this.stock = BigDecimal.ZERO;
        }
    }
    
    // Helper method to format quantity display
    public String formatQuantity(BigDecimal quantity) {
        if (quantity == null) return "0 " + unitOfMeasure;
        // Remove trailing zeros for cleaner display
        String qtyStr = quantity.stripTrailingZeros().toPlainString();
        return qtyStr + " " + unitOfMeasure;
    }
}
