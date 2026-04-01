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
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> seedDatabase() {
        log.info("Received request to seed database");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            databaseSeeder.seedAll();
            
            response.put("success", true);
            response.put("message", "Database seeded successfully");
            response.put("tenants", 3);
            response.put("usersPerTenant", 3);
            response.put("branchesPerTenant", 3);
            response.put("productsPerTenant", 20);
            response.put("productsPerBranch", "~6-7 products distributed across branches");
            response.put("salesData", "~300-450 receipts per branch over 3 months");
            response.put("inventoryData", "~150-240 transactions per branch over 3 months");
            response.put("loginInfo", Map.of(
                "tenants", new String[]{"technova", "freshmart", "stylehub"},
                "users", new String[]{"admin", "manager", "cashier"},
                "defaultPassword", "password123",
                "sampleLogin", "admin@technova.com / password123",
                "userAssignment", "Each user assigned to a different branch"
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
        info.put("description", "Seeds database with 3 tenants, each with 3 users, 3 branches, and 20 products");
        info.put("tenants", Map.of(
            "technova", "TechNova Electronics (electronics store)",
            "freshmart", "FreshMart Grocery (grocery store)",
            "stylehub", "StyleHub Fashion (clothing store)"
        ));
        info.put("defaultPassword", "password123");
        info.put("warning", "Only available in dev/test profiles");
        
        return ResponseEntity.ok(info);
    }
}
