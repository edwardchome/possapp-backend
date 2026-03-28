package com.possapp.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
    
    private String firstName;
    
    private String lastName;
    
    private String phoneNumber;
    
    private String role = "USER"; // Default role
    
    private Boolean canManageProducts; // null = false
    
    private Boolean canManageInventory; // null = false
}
