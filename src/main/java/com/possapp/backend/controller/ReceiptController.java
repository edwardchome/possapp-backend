package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.CreateSaleRequest;
import com.possapp.backend.dto.ReceiptDto;
import com.possapp.backend.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Receipts & Sales", description = "Sales processing and receipt management APIs")
@SecurityRequirement(name = "bearerAuth")
public class ReceiptController {
    
    private final ReceiptService receiptService;
    
    @GetMapping("/receipts")
    @Operation(
        summary = "Get all receipts",
        description = "Retrieve all receipts in the current tenant. Optional date range and branch filtering."
    )
    public ResponseEntity<ApiResponse<List<ReceiptDto>>> getAllReceipts(
            @Parameter(description = "Start date (ISO format)", required = false)
            @RequestParam(required = false) String startDate,
            @Parameter(description = "End date (ISO format)", required = false)
            @RequestParam(required = false) String endDate,
            @Parameter(description = "Branch ID to filter by", required = false)
            @RequestParam(required = false) String branchId) {
        
        List<ReceiptDto> receipts;
        if (startDate != null && endDate != null) {
            LocalDateTime start = LocalDateTime.parse(startDate);
            LocalDateTime end = LocalDateTime.parse(endDate);
            receipts = receiptService.getReceiptsByDateRange(start, end, branchId);
        } else if (branchId != null) {
            receipts = receiptService.getReceiptsByBranch(branchId);
        } else {
            receipts = receiptService.getAllReceipts();
        }
        return ResponseEntity.ok(ApiResponse.success(receipts));
    }
    
    @GetMapping("/receipts/paged")
    @Operation(
        summary = "Get receipts (paged)",
        description = "Retrieve paginated receipts"
    )
    public ResponseEntity<ApiResponse<Page<ReceiptDto>>> getAllReceiptsPaged(
            @Parameter(description = "Pagination parameters")
            Pageable pageable) {
        Page<ReceiptDto> receipts = receiptService.getAllReceipts(pageable);
        return ResponseEntity.ok(ApiResponse.success(receipts));
    }
    
    @GetMapping("/receipts/{id}")
    @Operation(
        summary = "Get receipt by ID",
        description = "Retrieve a specific receipt by ID"
    )
    public ResponseEntity<ApiResponse<ReceiptDto>> getReceiptById(
            @Parameter(description = "Receipt ID", required = true)
            @PathVariable String id) {
        ReceiptDto receipt = receiptService.getReceiptById(id);
        return ResponseEntity.ok(ApiResponse.success(receipt));
    }
    
    @PostMapping("/sales")
    @Operation(
        summary = "Process sale",
        description = "Process a new sale transaction and generate receipt"
    )
    public ResponseEntity<ApiResponse<ReceiptDto>> processSale(
            @Parameter(description = "Sale details", required = true)
            @Valid @RequestBody CreateSaleRequest request) {
        log.info("Processing sale with {} items", request.getItems().size());
        ReceiptDto receipt = receiptService.processSale(request);
        return ResponseEntity.ok(ApiResponse.success("Sale completed", receipt));
    }
    
    @PostMapping("/receipts/{id}/void")
    @Operation(
        summary = "Void receipt",
        description = "Void/cancel a previously created receipt"
    )
    public ResponseEntity<ApiResponse<ReceiptDto>> voidReceipt(
            @Parameter(description = "Receipt ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Void reason", required = true)
            @RequestBody Map<String, String> request) {
        String reason = request.getOrDefault("reason", "No reason provided");
        log.info("Voiding receipt: {} - Reason: {}", id, reason);
        ReceiptDto receipt = receiptService.voidReceipt(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Receipt voided", receipt));
    }
    
    @GetMapping("/receipts/search")
    @Operation(
        summary = "Search receipts",
        description = "Search receipts by customer info or receipt ID"
    )
    public ResponseEntity<ApiResponse<List<ReceiptDto>>> searchReceipts(
            @Parameter(description = "Search query", required = true)
            @RequestParam String query) {
        List<ReceiptDto> receipts = receiptService.searchReceipts(query);
        return ResponseEntity.ok(ApiResponse.success(receipts));
    }
    
    @GetMapping("/receipts/date-range")
    @Operation(
        summary = "Get receipts by date range",
        description = "Retrieve receipts within a specific date range. Optional branch filter for admins."
    )
    public ResponseEntity<ApiResponse<List<ReceiptDto>>> getReceiptsByDateRange(
            @Parameter(description = "Start date (ISO format)", required = true, example = "2024-01-01T00:00:00")
            @RequestParam String startDate,
            @Parameter(description = "End date (ISO format)", required = true, example = "2024-12-31T23:59:59")
            @RequestParam String endDate,
            @Parameter(description = "Branch ID to filter by (optional)", required = false)
            @RequestParam(required = false) String branchId) {
        LocalDateTime start = LocalDateTime.parse(startDate);
        LocalDateTime end = LocalDateTime.parse(endDate);
        List<ReceiptDto> receipts = receiptService.getReceiptsByDateRange(start, end, branchId);
        return ResponseEntity.ok(ApiResponse.success(receipts));
    }
    
    @GetMapping("/analytics/sales-report")
    @Operation(
        summary = "Get sales report",
        description = "Generate sales analytics report for a date range"
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSalesReport(
            @Parameter(description = "Start date (ISO format)", required = true, example = "2024-01-01T00:00:00")
            @RequestParam String startDate,
            @Parameter(description = "End date (ISO format)", required = true, example = "2024-12-31T23:59:59")
            @RequestParam String endDate) {
        LocalDateTime start = LocalDateTime.parse(startDate);
        LocalDateTime end = LocalDateTime.parse(endDate);
        Map<String, Object> report = receiptService.getSalesReport(start, end);
        return ResponseEntity.ok(ApiResponse.success(report));
    }
}
