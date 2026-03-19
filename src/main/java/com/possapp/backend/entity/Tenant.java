package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "company_name", nullable = false)
    private String companyName;
    
    @Column(name = "schema_name", nullable = false, unique = true)
    private String schemaName;
    
    @Column(name = "admin_email", nullable = false, unique = true)
    private String adminEmail;
    
    @Column(name = "contact_phone")
    private String contactPhone;
    
    @Column(name = "address")
    private String address;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @Column(name = "subscription_plan")
    @Builder.Default
    private String subscriptionPlan = "FREE";
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "subscription_expires_at")
    private LocalDateTime subscriptionExpiresAt;
}
