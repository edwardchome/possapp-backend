package com.possapp.backend.controller;

import com.possapp.backend.dto.ApiResponse;
import com.possapp.backend.dto.StoreConfigDto;
import com.possapp.backend.service.StoreConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/store")
@RequiredArgsConstructor
public class StoreConfigController {
    
    private final StoreConfigService storeConfigService;
    
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStoreConfig() {
        Map<String, Object> config = storeConfigService.getStoreConfigAsMap();
        return ResponseEntity.ok(ApiResponse.success(config));
    }
    
    @PutMapping("/config")
    public ResponseEntity<ApiResponse<StoreConfigDto>> updateStoreConfig(
            @RequestBody StoreConfigDto configDto) {
        log.info("Updating store configuration");
        StoreConfigDto updated = storeConfigService.updateStoreConfig(configDto);
        return ResponseEntity.ok(ApiResponse.success("Store configuration updated", updated));
    }
}
