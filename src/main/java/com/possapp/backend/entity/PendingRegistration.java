package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pending_registrations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingRegistration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(unique = true)
    private String verificationToken;
    
    private LocalDateTime tokenExpiryTime;
    
    private boolean emailVerified;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime verifiedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public boolean isTokenValid() {
        return tokenExpiryTime != null && LocalDateTime.now().isBefore(tokenExpiryTime);
    }
}
