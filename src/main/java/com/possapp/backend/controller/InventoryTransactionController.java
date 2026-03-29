package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.CreateInventoryRequest;
import com.possapp.backend.dto.InventoryTransactionDto;
import com.possapp.backend.service.InventoryTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory transaction management APIs")
@SecurityRequirement(name = "bearerAuth")
public class InventoryTransactionController {
    
    private final InventoryTransactionService inventoryService;
    
    @PostMapping("/add")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER', 'CAN_MANAGE_INVENTORY')")
    @Operation(
        summary = "Add stock to product",
        description = "Add inventory to an existing product (Admin/Manager or users with inventory permission)"
    )
    public ResponseEntity<ApiResponse<InventoryTransactionDto>> addStock(
            @Parameter(description = "Inventory addition details", required = true)
            @Valid @RequestBody CreateInventoryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        String createdBy = userDetails != null ? userDetails.getUsername() : "system";
        InventoryTransactionDto transaction = inventoryService.addStock(request, createdBy);
        
        return ResponseEntity.ok(ApiResponse.success(
            String.format("Added %s units. Stock: %s → %s", 
                transaction.getQuantity(), 
                transaction.getPreviousStock(), 
                transaction.getNewStock()),
            transaction
        ));
    }
    
    @GetMapping("/transactions")
    @Operation(
        summary = "Get all inventory transactions",
        description = "Retrieve all inventory transactions ordered by date"
    )
    public ResponseEntity<ApiResponse<List<InventoryTransactionDto>>> getAllTransactions() {
        List<InventoryTransactionDto> transactions = inventoryService.getAllTransactions();
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }
    
    @GetMapping("/transactions/product/{productCode}")
    @Operation(
        summary = "Get transactions by product",
        description = "Retrieve inventory transactions for a specific product"
    )
    public ResponseEntity<ApiResponse<List<InventoryTransactionDto>>> getTransactionsByProduct(
            @Parameter(description = "Product code", required = true, example = "PROD001")
            @PathVariable String productCode) {
        List<InventoryTransactionDto> transactions = inventoryService.getTransactionsByProduct(productCode);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }
    
    @GetMapping("/transactions/date-range")
    @Operation(
        summary = "Get transactions by date range",
        description = "Retrieve inventory transactions within a date range"
    )
    public ResponseEntity<ApiResponse<List<InventoryTransactionDto>>> getTransactionsByDateRange(
            @Parameter(description = "Start date (yyyy-MM-dd)", example = "2024-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (yyyy-MM-dd)", example = "2024-12-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        
        List<InventoryTransactionDto> transactions = inventoryService.getTransactionsByDateRange(start, end);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }
}
