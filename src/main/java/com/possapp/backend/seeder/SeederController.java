package com.possapp.backend.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for database seeding operations.
 * Only available in dev/test profiles for security.
 */
@Slf4j
@RestController
@RequestMapping("/api/seed")
@RequiredArgsConstructor
@Profile({"dev", "development", "test", "seed"})
public class SeederController {

    private final DatabaseSeeder databaseSeeder;

    /**
     * Seed the database with test data
     * POST /api/seed
     * POST /api/seed?force=true - Force reseed (drops existing tenants)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> seedDatabase(
            @RequestParam(name = "force", defaultValue = "false") boolean force) {
        log.info("Received request to seed database (force={})", force);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            databaseSeeder.seedAll(force);
            
            response.put("success", true);
            response.put("message", "Database seeded successfully");
            response.put("tenants", 3);
            response.put("productsPerTenant", 20);
            response.put("productsPerBranch", "~6-7 products distributed across branches");
            response.put("salesData", "~300-450 receipts per branch over 3 months");
            response.put("inventoryData", "~150-240 transactions per branch over 3 months");
            response.put("subscriptionPlans", Map.of(
                "technova", Map.of("plan", "STARTER", "users", 2, "branches", 1),
                "freshmart", Map.of("plan", "BUSINESS", "users", 3, "branches", 3),
                "stylehub", Map.of("plan", "ENTERPRISE", "users", 5, "branches", 5)
            ));
            response.put("loginInfo", Map.of(
                "technova", Map.of(
                    "plan", "STARTER",
                    "users", new String[]{"admin@technova.com", "cashier@technova.com"},
                    "branches", 1
                ),
                "freshmart", Map.of(
                    "plan", "BUSINESS",
                    "users", new String[]{"admin@freshmart.com", "manager@freshmart.com", "cashier@freshmart.com"},
                    "branches", 3
                ),
                "stylehub", Map.of(
                    "plan", "ENTERPRISE",
                    "users", new String[]{"admin@stylehub.com", "manager@stylehub.com", "cashier1@stylehub.com", "cashier2@stylehub.com", "supervisor@stylehub.com"},
                    "branches", 5
                ),
                "defaultPassword", "password123",
                "sampleLogin", "admin@technova.com / password123"
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Seeding failed: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Database seeding failed: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get seeding status/info
     * GET /api/seed
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSeedInfo() {
        Map<String, Object> info = new HashMap<>();
        
        info.put("available", true);
        info.put("endpoint", "POST /api/seed");
        info.put("description", "Seeds database with 3 tenants with different subscription plans");
        info.put("tenants", Map.of(
            "technova", Map.of("name", "TechNova Electronics", "plan", "STARTER", "users", 2, "branches", 1),
            "freshmart", Map.of("name", "FreshMart Grocery", "plan", "BUSINESS", "users", 3, "branches", 3),
            "stylehub", Map.of("name", "StyleHub Fashion", "plan", "ENTERPRISE", "users", 5, "branches", 5)
        ));
        info.put("defaultPassword", "password123");
        info.put("warning", "Only available in dev/test profiles");
        
        return ResponseEntity.ok(info);
    }
}
