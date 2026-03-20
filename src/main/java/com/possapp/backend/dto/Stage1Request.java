package com.possapp.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Stage1Request {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}
