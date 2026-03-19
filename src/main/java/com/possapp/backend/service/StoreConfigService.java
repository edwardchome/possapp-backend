package com.possapp.backend.service;

import com.possapp.backend.dto.StoreConfigDto;
import com.possapp.backend.entity.StoreConfig;
import com.possapp.backend.repository.StoreConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreConfigService {
    
    private final StoreConfigRepository storeConfigRepository;
    
    @Transactional(readOnly = true)
    public StoreConfig getStoreConfig() {
        return storeConfigRepository.findFirstByOrderByIdAsc()
            .orElseGet(() -> {
                // Create default config if none exists
                StoreConfig config = StoreConfig.builder().build();
                return storeConfigRepository.save(config);
            });
    }
    
    @Transactional(readOnly = true)
    public StoreConfigDto getStoreConfigDto() {
        return mapToDto(getStoreConfig());
    }
    
    @Transactional
    public StoreConfigDto updateStoreConfig(StoreConfigDto dto) {
        StoreConfig config = storeConfigRepository.findFirstByOrderByIdAsc()
            .orElse(StoreConfig.builder().build());
        
        config.setStoreName(dto.getStoreName());
        config.setStoreAddress(dto.getStoreAddress());
        config.setStorePhone(dto.getStorePhone());
        config.setStoreEmail(dto.getStoreEmail());
        config.setReceiptHeader(dto.getReceiptHeader());
        config.setReceiptFooter(dto.getReceiptFooter());
        config.setTaxRate(dto.getTaxRate());
        config.setCurrencyCode(dto.getCurrencyCode());
        config.setCurrencySymbol(dto.getCurrencySymbol());
        config.setTimezone(dto.getTimezone());
        config.setDateFormat(dto.getDateFormat());
        config.setTimeFormat(dto.getTimeFormat());
        config.setEnableReceiptPrinting(dto.isEnableReceiptPrinting());
        config.setEnableEmailReceipts(dto.isEnableEmailReceipts());
        config.setLowStockAlertThreshold(dto.getLowStockAlertThreshold());
        
        config = storeConfigRepository.save(config);
        log.info("Updated store configuration");
        return mapToDto(config);
    }
    
    @Transactional(readOnly = true)
    public Map<String, Object> getStoreConfigAsMap() {
        StoreConfig config = getStoreConfig();
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("storeName", config.getStoreName());
        map.put("storeAddress", config.getStoreAddress() != null ? config.getStoreAddress() : "");
        map.put("storePhone", config.getStorePhone() != null ? config.getStorePhone() : "");
        map.put("storeEmail", config.getStoreEmail() != null ? config.getStoreEmail() : "");
        map.put("receiptHeader", config.getReceiptHeader() != null ? config.getReceiptHeader() : "");
        map.put("receiptFooter", config.getReceiptFooter() != null ? config.getReceiptFooter() : "Thank you for your business!");
        map.put("taxRate", config.getTaxRate());
        map.put("currencyCode", config.getCurrencyCode());
        map.put("currencySymbol", config.getCurrencySymbol());
        map.put("timezone", config.getTimezone());
        map.put("dateFormat", config.getDateFormat());
        map.put("timeFormat", config.getTimeFormat());
        return map;
    }
    
    public StoreConfigDto mapToDto(StoreConfig config) {
        return StoreConfigDto.builder()
            .id(config.getId())
            .storeName(config.getStoreName())
            .storeAddress(config.getStoreAddress())
            .storePhone(config.getStorePhone())
            .storeEmail(config.getStoreEmail())
            .receiptHeader(config.getReceiptHeader())
            .receiptFooter(config.getReceiptFooter())
            .taxRate(config.getTaxRate())
            .currencyCode(config.getCurrencyCode())
            .currencySymbol(config.getCurrencySymbol())
            .timezone(config.getTimezone())
            .dateFormat(config.getDateFormat())
            .timeFormat(config.getTimeFormat())
            .enableReceiptPrinting(config.isEnableReceiptPrinting())
            .enableEmailReceipts(config.isEnableEmailReceipts())
            .lowStockAlertThreshold(config.getLowStockAlertThreshold())
            .updatedAt(config.getUpdatedAt())
            .build();
    }
}
