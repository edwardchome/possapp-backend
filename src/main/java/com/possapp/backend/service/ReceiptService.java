package com.possapp.backend.service;

import com.possapp.backend.dto.BranchDto;
import com.possapp.backend.dto.CreateSaleRequest;
import com.possapp.backend.dto.ReceiptDto;
import com.possapp.backend.dto.ReceiptItemDto;
import com.possapp.backend.entity.Branch;
import com.possapp.backend.entity.Product;
import com.possapp.backend.entity.Receipt;
import com.possapp.backend.entity.ReceiptItem;
import com.possapp.backend.entity.User;
import com.possapp.backend.exception.ReceiptException;
import com.possapp.backend.repository.BranchRepository;
import com.possapp.backend.repository.ProductRepository;
import com.possapp.backend.repository.ReceiptRepository;
import com.possapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {
    
    private final ReceiptRepository receiptRepository;
    private final ProductRepository productRepository;
    private final StoreConfigService storeConfigService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    
    @Transactional(readOnly = true)
    public List<ReceiptDto> getAllReceipts() {
        return receiptRepository.findAllByVoidedFalseOrderByTimestampDescWithBranch()
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public Page<ReceiptDto> getAllReceipts(Pageable pageable) {
        return receiptRepository.findAllByVoidedFalseOrderByTimestampDesc(pageable)
            .map(this::mapToDto);
    }
    
    @Transactional(readOnly = true)
    public ReceiptDto getReceiptById(String id) {
        Receipt receipt = receiptRepository.findByIdAndVoidedFalseWithBranch(id)
            .orElseThrow(() -> new ReceiptException("Receipt not found: " + id));
        return mapToDto(receipt);
    }
    
    @Transactional(readOnly = true)
    public Receipt findReceiptById(String id) {
        return receiptRepository.findByIdAndVoidedFalse(id)
            .orElseThrow(() -> new ReceiptException("Receipt not found: " + id));
    }
    
    @Transactional
    public ReceiptDto processSale(CreateSaleRequest request) {
        // Validate stock availability
        for (CreateSaleRequest.SaleItemRequest item : request.getItems()) {
            Product product = productRepository.findByCodeAndActiveTrue(item.getCode())
                .orElseThrow(() -> new ReceiptException("Product not found: " + item.getCode()));
            
            BigDecimal requestedQty = item.getQuantity();
            if (product.getStock().compareTo(requestedQty) < 0) {
                throw new ReceiptException(
                    String.format("Insufficient stock for %s. Available: %s, Requested: %s",
                        product.getName(), product.getStock(), requestedQty));
            }
        }
        
        // Get store config for tax rate
        var storeConfig = storeConfigService.getStoreConfig();
        BigDecimal taxRate = storeConfig != null ? storeConfig.getTaxRate() : BigDecimal.ZERO;
        
        // Create receipt
        String receiptId = "RCP-" + System.currentTimeMillis();
        Receipt receipt = Receipt.builder()
            .id(receiptId)
            .customerName(request.getCustomerName())
            .customerEmail(request.getCustomerEmail())
            .customerPhone(request.getCustomerPhone())
            .paymentMethod(request.getPaymentMethod())
            .notes(request.getNotes())
            .taxRate(taxRate)
            .discount(request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO)
            .build();
        
        // Get current user info and set branch
        var currentUserDto = userService.getCurrentUser();
        if (currentUserDto != null) {
            receipt.setCashierId(currentUserDto.getId());
            receipt.setCashierName(currentUserDto.getFullName());
            
            // Get the full user entity to access branch relationships
            User currentUser = userRepository.findByEmailWithBranch(currentUserDto.getEmail())
                .orElse(null);
            
            // Set branch from user's active branch
            if (currentUser != null) {
                if (currentUser.getActiveBranch() != null) {
                    receipt.setBranch(currentUser.getActiveBranch());
                } else if (currentUser.getBranch() != null) {
                    receipt.setBranch(currentUser.getBranch());
                }
            }
        }
        
        // Process items and update stock
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CreateSaleRequest.SaleItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findByCodeAndActiveTrue(itemRequest.getCode())
                .orElseThrow(() -> new ReceiptException("Product not found: " + itemRequest.getCode()));
            
            // Update stock
            BigDecimal saleQty = itemRequest.getQuantity();
            product.setStock(product.getStock().subtract(saleQty));
            productRepository.save(product);
            
            // Create receipt item - use selling price, not cost price
            BigDecimal sellingPrice = product.getSellingPrice() != null ? product.getSellingPrice() : product.getPrice();
            ReceiptItem item = ReceiptItem.builder()
                .productCode(product.getCode())
                .productName(product.getName())
                .price(sellingPrice)
                .qty(saleQty)
                .lineTotal(sellingPrice.multiply(saleQty))
                .build();
            
            receipt.addItem(item);
            subtotal = subtotal.add(item.getLineTotal());
        }
        
        receipt.setSubtotal(subtotal);
        receipt.calculateTotals();
        
        receipt = receiptRepository.save(receipt);
        log.info("Processed sale: {} with {} items, total: {}", 
            receipt.getId(), receipt.getItems().size(), receipt.getTotal());
        
        return mapToDto(receipt);
    }
    
    @Transactional
    public ReceiptDto voidReceipt(String id, String reason) {
        Receipt receipt = receiptRepository.findById(id)
            .orElseThrow(() -> new ReceiptException("Receipt not found: " + id));
        
        if (receipt.isVoided()) {
            throw new ReceiptException("Receipt is already voided");
        }
        
        // Restore stock
        for (ReceiptItem item : receipt.getItems()) {
            productRepository.findByCodeAndActiveTrue(item.getProductCode())
                .ifPresent(product -> {
                    BigDecimal returnQty = item.getQty();
                    product.setStock(product.getStock().add(returnQty));
                    productRepository.save(product);
                });
        }
        
        receipt.voidReceipt(reason);
        receipt = receiptRepository.save(receipt);
        
        log.info("Voided receipt: {} - Reason: {}", id, reason);
        return mapToDto(receipt);
    }
    
    @Transactional(readOnly = true)
    public List<ReceiptDto> getReceiptsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return receiptRepository.findByDateRangeWithBranch(startDate, endDate)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReceiptDto> searchReceipts(String query) {
        return receiptRepository.searchReceipts(query)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public Map<String, Object> getSalesReport(LocalDateTime startDate, LocalDateTime endDate) {
        Double totalSales = receiptRepository.calculateTotalSales(startDate, endDate);
        Long receiptCount = receiptRepository.countReceipts(startDate, endDate);
        
        return Map.of(
            "totalSales", totalSales != null ? totalSales : 0.0,
            "receiptCount", receiptCount != null ? receiptCount : 0L,
            "startDate", startDate.format(DateTimeFormatter.ISO_DATE),
            "endDate", endDate.format(DateTimeFormatter.ISO_DATE)
        );
    }
    
    public ReceiptDto mapToDto(Receipt receipt) {
        BranchDto branchDto = null;
        if (receipt.getBranch() != null) {
            branchDto = BranchDto.builder()
                .id(receipt.getBranch().getId())
                .name(receipt.getBranch().getName())
                .code(receipt.getBranch().getCode())
                .build();
        }
        
        return ReceiptDto.builder()
            .id(receipt.getId())
            .timestamp(receipt.getTimestamp())
            .items(receipt.getItems().stream()
                .map(this::mapItemToDto)
                .collect(Collectors.toList()))
            .subtotal(receipt.getSubtotal())
            .tax(receipt.getTax())
            .taxRate(receipt.getTaxRate())
            .discount(receipt.getDiscount())
            .total(receipt.getTotal())
            .customerName(receipt.getCustomerName())
            .customerEmail(receipt.getCustomerEmail())
            .customerPhone(receipt.getCustomerPhone())
            .paymentMethod(receipt.getPaymentMethod())
            .paymentStatus(receipt.getPaymentStatus())
            .notes(receipt.getNotes())
            .cashierName(receipt.getCashierName())
            .voided(receipt.isVoided())
            .voidedAt(receipt.getVoidedAt())
            .voidReason(receipt.getVoidReason())
            .createdAt(receipt.getCreatedAt())
            .itemCount(receipt.getItems().size())
            .branch(branchDto)
            .build();
    }
    
    private ReceiptItemDto mapItemToDto(ReceiptItem item) {
        return ReceiptItemDto.builder()
            .id(item.getId())
            .productCode(item.getProductCode())
            .productName(item.getProductName())
            .price(item.getPrice())
            .qty(item.getQty())
            .lineTotal(item.getLineTotal())
            .build();
    }
}
