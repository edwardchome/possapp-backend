package com.possapp.backend.service;

import com.possapp.backend.dto.CreateInventoryRequest;
import com.possapp.backend.dto.InventoryTransactionDto;
import com.possapp.backend.entity.InventoryTransaction;
import com.possapp.backend.entity.Product;
import com.possapp.backend.exception.ProductException;
import com.possapp.backend.repository.InventoryTransactionRepository;
import com.possapp.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryTransactionService {
    
    private final InventoryTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    
    @Transactional
    public InventoryTransactionDto addStock(CreateInventoryRequest request, String createdBy) {
        Product product = productRepository.findById(request.getProductCode())
            .orElseThrow(() -> new ProductException("Product not found: " + request.getProductCode()));
        
        BigDecimal previousStock = product.getStock();
        BigDecimal newStock = previousStock.add(request.getQuantity());
        
        // Update product stock
        product.setStock(newStock);
        productRepository.save(product);
        
        // Calculate total cost
        BigDecimal totalCost = null;
        if (request.getUnitCost() != null) {
            totalCost = request.getUnitCost().multiply(request.getQuantity());
        }
        
        // Create transaction record
        InventoryTransaction transaction = InventoryTransaction.builder()
            .productCode(request.getProductCode())
            .quantity(request.getQuantity())
            .previousStock(previousStock)
            .newStock(newStock)
            .unitCost(request.getUnitCost())
            .totalCost(totalCost)
            .referenceNumber(request.getReferenceNumber())
            .supplierName(request.getSupplierName())
            .notes(request.getNotes())
            .type(InventoryTransaction.TransactionType.STOCK_IN)
            .createdBy(createdBy)
            .build();
        
        transaction = transactionRepository.save(transaction);
        log.info("Added {} units to product {}. Stock: {} -> {}", 
            request.getQuantity(), request.getProductCode(), previousStock, newStock);
        
        return mapToDto(transaction);
    }
    
    @Transactional(readOnly = true)
    public List<InventoryTransactionDto> getTransactionsByProduct(String productCode) {
        return transactionRepository.findByProductCodeOrderByCreatedAtDesc(productCode)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<InventoryTransactionDto> getAllTransactions() {
        return transactionRepository.findAll()
            .stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<InventoryTransactionDto> getTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return getTransactionsByDateRange(startDate, endDate, null);
    }
    
    @Transactional(readOnly = true)
    public List<InventoryTransactionDto> getTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate, String branchId) {
        List<InventoryTransaction> transactions;
        if (branchId != null && !branchId.isEmpty()) {
            transactions = transactionRepository.findByDateRangeAndBranch(startDate, endDate, branchId);
        } else {
            transactions = transactionRepository.findByDateRange(startDate, endDate);
        }
        return transactions.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<InventoryTransactionDto> getTransactionsByBranch(String branchId) {
        return transactionRepository.findByBranchId(branchId)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }
    
    private InventoryTransactionDto mapToDto(InventoryTransaction transaction) {
        InventoryTransactionDto.InventoryTransactionDtoBuilder builder = InventoryTransactionDto.builder()
            .id(transaction.getId())
            .productCode(transaction.getProductCode())
            .quantity(transaction.getQuantity())
            .previousStock(transaction.getPreviousStock())
            .newStock(transaction.getNewStock())
            .unitCost(transaction.getUnitCost())
            .totalCost(transaction.getTotalCost())
            .referenceNumber(transaction.getReferenceNumber())
            .supplierName(transaction.getSupplierName())
            .notes(transaction.getNotes())
            .type(transaction.getType().name())
            .createdBy(transaction.getCreatedBy())
            .createdAt(transaction.getCreatedAt());
        
        if (transaction.getProduct() != null) {
            builder.productName(transaction.getProduct().getName());
        }
        
        return builder.build();
    }
}
