package com.possapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Authentication response with JWT tokens")
public class AuthResponse {
    
    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;
    
    @Schema(description = "JWT refresh token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String refreshToken;
    
    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;
    
    @Schema(description = "Token expiration time in seconds", example = "86400")
    private Long expiresIn;
    
    @Schema(description = "Authenticated user details")
    private UserDto user;
    
    @Schema(description = "Response timestamp")
    private LocalDateTime timestamp;
}
