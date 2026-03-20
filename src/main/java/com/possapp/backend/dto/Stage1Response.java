package com.possapp.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Stage1Response {
    private String message;
    private String email;
    private boolean emailSent;
    private boolean emailAlreadyVerified;
    private String verificationToken;
}
