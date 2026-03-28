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
    
    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    
    @Column(length = 2000)
    private String description;
    
    @Column(name = "image_url")
    private String imageUrl;
    
    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;
    
    @Column(name = "min_stock_level")
    @Builder.Default
    private Integer minStockLevel = 0;
    
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
        return stock <= minStockLevel;
    }
    
    // Helper method to adjust stock
    public void adjustStock(int delta) {
        this.stock = Math.max(0, this.stock + delta);
    }
}
