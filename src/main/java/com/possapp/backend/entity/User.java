package com.possapp.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(nullable = false)
    @Builder.Default
    private String role = "USER"; // USER, ADMIN, MANAGER
    
    @Column(name = "can_manage_products", nullable = false)
    @Builder.Default
    private boolean canManageProducts = false; // Add/edit products
    
    @Column(name = "can_manage_inventory", nullable = false)
    @Builder.Default
    private boolean canManageInventory = false; // Add inventory
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;
    
    @Column(name = "email_verification_token")
    private String emailVerificationToken;
    
    @Column(name = "email_verification_expiry")
    private LocalDateTime emailVerificationExpiry;
    
    @Column(name = "password_change_required", nullable = false)
    @Builder.Default
    private boolean passwordChangeRequired = false;
    
    @Column(name = "password_reset_token")
    private String passwordResetToken;
    
    @Column(name = "password_reset_expiry")
    private LocalDateTime passwordResetExpiry;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "permissions_version", nullable = false)
    @Builder.Default
    private Long permissionsVersion = 1L;
    
    /**
     * Branch assignment for the user.
     * Optional - if null, user can work at any branch.
     * If set, user is primarily associated with this branch.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;
    
    public String getFullName() {
        if (firstName == null && lastName == null) return email;
        return String.format("%s %s", 
            firstName != null ? firstName : "", 
            lastName != null ? lastName : "").trim();
    }
    
    public boolean isEmailVerificationTokenValid() {
        if (emailVerificationToken == null || emailVerificationExpiry == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(emailVerificationExpiry);
    }
    
    public boolean isPasswordResetTokenValid() {
        if (passwordResetToken == null || passwordResetExpiry == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(passwordResetExpiry);
    }
}
