package com.possapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================================
 * USAGE DTO
 * ============================================================================
 * Represents resource usage statistics for a tenant.
 * Shows current usage vs limits with canAdd flag for UI enable/disable.
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageDto {

    private ResourceUsage users;
    private ResourceUsage branches;
    private ResourceUsage products;
    private ResourceUsage monthlyTransactions;

    /**
     * Nested class for individual resource usage.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResourceUsage {
        private int used;
        private Integer limit; // null means unlimited
        private boolean canAdd;
        private int remaining; // -1 means unlimited
        
        /**
         * Create ResourceUsage with calculated fields.
         */
        public static ResourceUsage of(int used, Integer limit) {
            boolean unlimited = limit == null || limit < 0;
            boolean canAdd = unlimited || used < limit;
            int remaining = unlimited ? -1 : Math.max(0, limit - used);
            
            return ResourceUsage.builder()
                    .used(used)
                    .limit(unlimited ? null : limit)
                    .canAdd(canAdd)
                    .remaining(remaining)
                    .build();
        }
    }

    /**
     * Create empty usage (all zeros).
     */
    public static UsageDto empty() {
        return UsageDto.builder()
                .users(ResourceUsage.of(0, 0))
                .branches(ResourceUsage.of(0, 0))
                .products(ResourceUsage.of(0, 0))
                .monthlyTransactions(ResourceUsage.of(0, 0))
                .build();
    }
}
