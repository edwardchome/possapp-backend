package com.possapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantDto {
    
    private UUID id;
    private String companyName;
    private String schemaName;
    private String adminEmail;
    private String contactPhone;
    private String address;
    private boolean active;
    private String subscriptionPlan;
    private LocalDateTime createdAt;
    private LocalDateTime subscriptionExpiresAt;
}
