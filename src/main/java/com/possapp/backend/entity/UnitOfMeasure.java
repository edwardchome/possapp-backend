package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "units_of_measure")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitOfMeasure {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(nullable = false, unique = true)
    private String symbol;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitType type;
    
    private String description;
    
    @Column(name = "allow_fractions", nullable = false)
    @Builder.Default
    private boolean allowFractions = false;
    
    @Column(name = "default_precision")
    private Integer defaultPrecision = 0;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum UnitType {
        COUNT, WEIGHT, VOLUME, LENGTH, AREA, TIME, OTHER
    }
}
