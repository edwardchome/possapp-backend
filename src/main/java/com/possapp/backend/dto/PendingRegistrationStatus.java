package com.possapp.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PendingRegistrationStatus {
    private String email;
    private boolean emailVerified;
    private String verificationToken;
    private boolean tokenValid;
    private LocalDateTime expiryTime;
}
