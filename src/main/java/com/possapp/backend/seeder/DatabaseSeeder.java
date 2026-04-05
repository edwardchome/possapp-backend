package com.possapp.backend.seeder;

import com.possapp.backend.dto.TenantRegistrationRequest;
import com.possapp.backend.entity.Branch;
import com.possapp.backend.entity.Category;
import com.possapp.backend.entity.Product;
import com.possapp.backend.dto.TenantDto;
import com.possapp.backend.entity.SubscriptionPlan;
import com.possapp.backend.entity.SubscriptionStatus;
import com.possapp.backend.entity.Tenant;
import com.possapp.backend.entity.TenantUsage;
import com.possapp.backend.entity.UnitOfMeasure;
import com.possapp.backend.entity.User;

import com.possapp.backend.repository.BranchRepository;
import com.possapp.backend.repository.CategoryRepository;
import com.possapp.backend.repository.ProductRepository;
import com.possapp.backend.repository.TenantRepository;
import com.possapp.backend.repository.TenantUsageRepository;
import com.possapp.backend.repository.UnitOfMeasureRepository;
import com.possapp.backend.repository.UserRepository;
import com.possapp.backend.service.TenantService;
import com.possapp.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Database Seeder for Development/Testing
 * 
 * Seeds the database with:
 * - 3 tenants (TechNova, FreshMart, StyleHub)
 * - 3 users per tenant (admin, manager, cashier)
 * - 3 branches per tenant
 * - 8 categories per tenant
 * - 8 units of measure per tenant
 * - 20 products per tenant
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"dev", "development", "test", "seed"})
public class DatabaseSeeder {

    private final TenantService tenantService;
    private final TenantRepository tenantRepository;
    private final TenantUsageRepository tenantUsageRepository;
    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    private static final String DEFAULT_PASSWORD = "password123";

    /**
     * Main entry point to seed all data
     * Note: Not @Transactional because TenantService.registerTenant handles its own transactions
     */
    public void seedAll() {
        log.info("========================================");
        log.info("Starting Database Seeding...");
        log.info("========================================");
        
        try {
            // Tenant 1: TechNova Electronics - STARTER plan
            seedTenant(new TenantConfig(
                "technova",
                "TechNova Electronics",
                "admin@technova.com",
                SubscriptionPlan.STARTER,
                Arrays.asList(
                    new UserData("admin@technova.com", "Admin", "User", "ADMIN"),
                    new UserData("cashier@technova.com", "Cashier", "User", "USER")
                ),
                Arrays.asList(
                    new BranchData("Main Store", "MAIN", true, true)
                ),
                getElectronicsProducts()
            ));

            // Tenant 2: FreshMart Grocery - BUSINESS plan
            seedTenant(new TenantConfig(
                "freshmart",
                "FreshMart Grocery",
                "admin@freshmart.com",
                SubscriptionPlan.BUSINESS,
                Arrays.asList(
                    new UserData("admin@freshmart.com", "Admin", "User", "ADMIN"),
                    new UserData("manager@freshmart.com", "Manager", "User", "MANAGER"),
                    new UserData("cashier@freshmart.com", "Cashier", "User", "USER")
                ),
                Arrays.asList(
                    new BranchData("Main Store", "MAIN", true, true),
                    new BranchData("Express Branch", "EXP", false, true),
                    new BranchData("Storage Facility", "STOR", false, false)
                ),
                getGroceryProducts()
            ));

            // Tenant 3: StyleHub Fashion - ENTERPRISE plan
            seedTenant(new TenantConfig(
                "stylehub",
                "StyleHub Fashion",
                "admin@stylehub.com",
                SubscriptionPlan.ENTERPRISE,
                Arrays.asList(
                    new UserData("admin@stylehub.com", "Admin", "User", "ADMIN"),
                    new UserData("manager@stylehub.com", "Manager", "User", "MANAGER"),
                    new UserData("cashier1@stylehub.com", "Cashier", "One", "USER"),
                    new UserData("cashier2@stylehub.com", "Cashier", "Two", "USER"),
                    new UserData("supervisor@stylehub.com", "Supervisor", "User", "MANAGER")
                ),
                Arrays.asList(
                    new BranchData("Mall Store", "MALL", true, true),
                    new BranchData("Street Shop", "STR", false, true),
                    new BranchData("Online Warehouse", "ONL", false, false),
                    new BranchData("Airport Store", "AIR", false, true),
                    new BranchData("Outlet Store", "OUT", false, true)
                ),
                getFashionProducts()
            ));

            log.info("========================================");
            log.info("Database Seeding Completed Successfully!");
            log.info("========================================");
            log.info("Created 3 tenants with different subscription plans:");
            log.info("  1. TechNova Electronics - STARTER plan (2 users, 1 branch)");
            log.info("  2. FreshMart Grocery - BUSINESS plan (3 users, 3 branches)");
            log.info("  3. StyleHub Fashion - ENTERPRISE plan (5 users, 5 branches)");
            log.info("Each tenant has:");
            log.info("  - 20 products (~6-7 per branch, distributed)");
            log.info("  - ~300-450 sales receipts over 3 months per branch");
            log.info("  - ~150-240 inventory transactions over 3 months per branch");
            log.info("Login credentials:");
            log.info("  - TechNova: admin@technova.com, cashier@technova.com / {}", DEFAULT_PASSWORD);
            log.info("  - FreshMart: admin@freshmart.com, manager@freshmart.com, cashier@freshmart.com / {}", DEFAULT_PASSWORD);
            log.info("  - StyleHub: admin@stylehub.com, manager@stylehub.com, cashier1@stylehub.com, cashier2@stylehub.com, supervisor@stylehub.com / {}", DEFAULT_PASSWORD);
            
        } catch (Exception e) {
            log.error("Error during database seeding: {}", e.getMessage(), e);
            throw new RuntimeException("Database seeding failed", e);
        }
    }

    private void seedTenant(TenantConfig config) {
        log.info("\n----------------------------------------");
        log.info("Seeding tenant: {}", config.schemaName);
        log.info("----------------------------------------");
        
        // Check if tenant already exists in public.tenants
        if (tenantExists(config.schemaName)) {
            log.warn("Tenant '{}' already exists in public.tenants. Skipping.", config.schemaName);
            return;
        }
        
        // Drop schema if it exists from previous failed runs (dev only)
        dropSchemaIfExists(config.schemaName);
        
        // Create tenant
        TenantRegistrationRequest request = new TenantRegistrationRequest();
        request.setSchemaName(config.schemaName);
        request.setCompanyName(config.companyName);
        request.setAdminEmail(config.adminEmail);
        request.setPassword(DEFAULT_PASSWORD);
        request.setSubscriptionPlan(config.subscriptionPlan.name());
        
        TenantDto tenant = tenantService.registerTenant(request);
        log.info("✓ Created tenant: {} (schema: {}) with {} plan", 
            tenant.getCompanyName(), config.schemaName, config.subscriptionPlan.getDisplayName());
        
        // Update tenant subscription plan and create usage record
        updateTenantSubscription(config.schemaName, config.subscriptionPlan, config.users.size(), config.branches.size());
        
        // Set tenant context for subsequent operations
        TenantContext.setCurrentTenant(config.schemaName);
        
        try (Connection conn = dataSource.getConnection()) {
            // Get the main branch ID that was already created by registerTenant
            String mainBranchId = getMainBranchId(config.schemaName);
            log.info("  ✓ Using existing main branch: {}", mainBranchId);
            
            // Create additional branches (skip main branch, it already exists)
            List<BranchData> additionalBranches = config.branches.stream()
                .filter(b -> !b.isMain())
                .toList();
            List<String> additionalBranchIds = createAdditionalBranches(config.schemaName, additionalBranches);
            
            // Combine all branch IDs (main + additional)
            List<String> allBranchIds = new ArrayList<>();
            allBranchIds.add(mainBranchId);
            allBranchIds.addAll(additionalBranchIds);
            
            // Create additional users (manager and cashier) - admin is already created by registerTenant
            createAdditionalUsers(config.schemaName, config.users, allBranchIds);
            
            // Create categories
            createCategories(config.schemaName);
            
            // Note: Default units of measure are already created by TenantService.registerTenant()
            // createUnitsOfMeasure(config.schemaName);
            
            // Create products for each branch (distribute products across branches)
            createProductsForBranches(config.schemaName, config.products, allBranchIds);
            
            // Get product codes for sales and inventory (all products from all branches)
            List<String> productCodes = getProductCodes(config.schemaName);
            
            // Create 3 months of sales data for each branch
            createSalesData(config.schemaName, allBranchIds, productCodes, config.users);
            
            // Create 3 months of inventory transactions for each branch
            createInventoryTransactions(config.schemaName, allBranchIds, productCodes);
            
            log.info("✓ Completed seeding tenant: {}", config.schemaName);
            
        } catch (Exception e) {
            log.error("Error seeding tenant {}: {}", config.schemaName, e.getMessage(), e);
            throw new RuntimeException("Failed to seed tenant: " + config.schemaName, e);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean tenantExists(String schemaName) {
        String sql = "SELECT COUNT(*) FROM public.tenants WHERE schema_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, schemaName);
        return count != null && count > 0;
    }
    
    private void dropSchemaIfExists(String schemaName) {
        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
            log.info("  Dropped existing schema: {}", schemaName);
        } catch (Exception e) {
            // Schema might not exist, ignore
        }
    }

    /**
     * Update tenant subscription plan and create usage record
     * For seeded tenants, we set them as ACTIVE with a 1-year period (not in trial)
     * This ensures they have full functionality for development/testing
     */
    private void updateTenantSubscription(String schemaName, SubscriptionPlan plan, int userCount, int branchCount) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime periodEnd = now.plusYears(1);
            
            // Update tenant subscription in public.tenants
            // Seeded tenants are set to ACTIVE (not TRIAL) for immediate full access
            String updateSql = "UPDATE public.tenants SET subscription_plan = ?, subscription_status = ?, " +
                "subscription_started_at = ?, current_period_end = ?, trial_ends_at = NULL, " +
                "grace_period_ends_at = NULL, trial_reminder_sent = false, " +
                "trial_ended_notification_sent = false, grace_period_notification_sent = false " +
                "WHERE schema_name = ?";
            jdbcTemplate.update(updateSql, plan.name(), SubscriptionStatus.ACTIVE.name(), now, periodEnd, schemaName);
            
            // Get tenant ID
            String tenantId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.tenants WHERE schema_name = ?", String.class, schemaName);
            
            // Create or update tenant usage record
            String usageSql = "INSERT INTO public.tenant_usage (id, tenant_id, current_users, current_branches, current_products, current_monthly_transactions) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, 0, 0) " +
                "ON CONFLICT (tenant_id) DO UPDATE SET " +
                "current_users = EXCLUDED.current_users, " +
                "current_branches = EXCLUDED.current_branches, " +
                "calculated_at = CURRENT_TIMESTAMP";
            jdbcTemplate.update(usageSql, tenantId, userCount, branchCount);
            
            log.info("  ✓ Set subscription to {} (ACTIVE, expires {}) with usage: {} users, {} branches", 
                plan.getDisplayName(), periodEnd.toLocalDate(), userCount, branchCount);
        } catch (Exception e) {
            log.warn("  ⚠ Could not update subscription for {}: {}", schemaName, e.getMessage());
        }
    }

    private String getMainBranchId(String schemaName) {
        String sql = String.format(
            "SELECT id FROM %s.branches WHERE is_main_branch = true LIMIT 1",
            schemaName
        );
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    private List<String> createAdditionalBranches(String schemaName, List<BranchData> branches) {
        List<String> branchIds = new ArrayList<>();
        for (BranchData data : branches) {
            String id = UUID.randomUUID().toString();
            String sql = String.format(
                "INSERT INTO %s.branches (id, name, code, is_main_branch, is_active, can_sell, created_at, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", schemaName
            );
            
            jdbcTemplate.update(sql, id, data.getName(), data.getCode(), 
                data.isMain(), data.isActive(), data.isCanSell(), 
                LocalDateTime.now(), "system");
            
            branchIds.add(id);
            log.info("  ✓ Created branch: {} ({})", data.getName(), data.getCode());
        }
        return branchIds;
    }
    
    private void createAdditionalUsers(String schemaName, List<UserData> users, List<String> branchIds) {
        // Skip first user (admin) as it's already created by registerTenant
        // Assign each remaining user to a different branch
        for (int i = 1; i < users.size() && i <= branchIds.size(); i++) {
            UserData user = users.get(i);
            String branchId = branchIds.get(i - 1); // i-1 because admin uses main branch (index 0)
            
            String sql = String.format(
                "INSERT INTO %s.users (id, email, password, first_name, last_name, role, is_active, " +
                "email_verified, password_change_required, branch_id, active_branch_id, permissions_version, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                schemaName
            );
            
            String encodedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);
            
            jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                user.getEmail(),
                encodedPassword,
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                true,  // is_active
                true,  // email_verified
                false, // password_change_required
                branchId,
                branchId,
                1,     // permissions_version
                LocalDateTime.now()
            );
            
            log.info("  ✓ Created user: {} ({}) assigned to branch: {}", 
                user.getEmail(), user.getRole(), branchId);
        }
    }

    private void createCategories(String schemaName) {
        // Note: "General" category is already created by TenantService.registerTenant()
        List<String> categories = Arrays.asList(
            "Electronics", "Accessories", "Beverages", 
            "Food", "Clothing", "Shoes", "Home & Garden"
        );
        
        for (String name : categories) {
            String sql = String.format(
                "INSERT INTO %s.categories (id, name, is_active, created_at) VALUES (?, ?, ?, ?)",
                schemaName
            );
            jdbcTemplate.update(sql, UUID.randomUUID().toString(), name, true, LocalDateTime.now());
        }
        
        log.info("  ✓ Created {} categories (plus General from tenant creation)", categories.size());
    }

    private void createUnitsOfMeasure(String schemaName) {
        List<UnitData> units = Arrays.asList(
            new UnitData("Pieces", "pcs", "count", false, 0),
            new UnitData("Kilograms", "kg", "weight", true, 3),
            new UnitData("Grams", "g", "weight", false, 0),
            new UnitData("Liters", "LT", "volume", true, 3),
            new UnitData("Milliliters", "ml", "volume", false, 0),
            new UnitData("Meters", "m", "length", true, 2),
            new UnitData("Boxes", "box", "count", false, 0),
            new UnitData("Packs", "pack", "count", false, 0)
        );
        
        for (UnitData unit : units) {
            String sql = String.format(
                "INSERT INTO %s.units_of_measure (id, name, symbol, type, allow_fractions, default_precision, is_active, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                schemaName
            );
            jdbcTemplate.update(sql, UUID.randomUUID().toString(), unit.getName(), unit.getSymbol(),
                unit.getType(), unit.isAllowFractions(), unit.getPrecision(), true, LocalDateTime.now());
        }
        
        log.info("  ✓ Created {} units of measure", units.size());
    }

    private void createProductsForBranches(String schemaName, List<ProductData> products, List<String> branchIds) {
        // Get General category ID
        String categorySql = String.format(
            "SELECT id FROM %s.categories WHERE name = 'General' LIMIT 1",
            schemaName
        );
        String categoryId = jdbcTemplate.queryForObject(categorySql, String.class);
        
        int productsPerBranch = products.size() / branchIds.size();
        int productIndex = 0;
        
        for (int i = 0; i < branchIds.size(); i++) {
            String branchId = branchIds.get(i);
            int startIdx = i * productsPerBranch;
            int endIdx = (i == branchIds.size() - 1) ? products.size() : startIdx + productsPerBranch;
            
            for (int j = startIdx; j < endIdx; j++) {
                ProductData product = products.get(j);
                
                String sql = String.format(
                    "INSERT INTO %s.products (code, name, price, selling_price, stock, category_id, branch_id, " +
                    "unit_of_measure, allow_decimal, min_quantity, step_quantity, is_active, created_at, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    schemaName
                );
                
                jdbcTemplate.update(sql, 
                    product.getCode(),
                    product.getName(),
                    product.getPrice(),
                    product.getSellingPrice(),
                    product.getStock(),
                    categoryId,
                    branchId,
                    "pcs",
                    false,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    true,
                    LocalDateTime.now(),
                    "system"
                );
            }
            
            int count = endIdx - startIdx;
            log.info("  ✓ Created {} products for branch {}", count, branchId.substring(0, 8) + "...");
        }
        
        log.info("  ✓ Total {} products distributed across {} branches", products.size(), branchIds.size());
    }

    private List<String> getProductCodes(String schemaName) {
        String sql = String.format("SELECT code FROM %s.products WHERE is_active = true", schemaName);
        return jdbcTemplate.queryForList(sql, String.class);
    }

    private void createSalesData(String schemaName, List<String> branchIds, List<String> productCodes, List<UserData> users) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusMonths(3);
        
        int totalReceipts = 0;
        int totalItems = 0;
        
        // Get user emails for assignment
        List<String> userEmails = users.stream()
            .map(UserData::getEmail)
            .toList();
        
        for (String branchId : branchIds) {
            // Generate ~100-150 receipts per branch over 3 months
            int receiptsForBranch = 100 + (int)(Math.random() * 50);
            
            for (int i = 0; i < receiptsForBranch; i++) {
                // Random date within 3 months
                LocalDateTime receiptDate = startDate.plusSeconds(
                    (long)(Math.random() * java.time.Duration.between(startDate, endDate).getSeconds())
                );
                
                // Generate receipt
                String receiptId = "RCP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String cashierEmail = userEmails.get((int)(Math.random() * userEmails.size()));
                
                // 3-8 items per receipt
                int itemCount = 3 + (int)(Math.random() * 6);
                BigDecimal subtotal = BigDecimal.ZERO;
                
                // Create receipt items first to calculate totals
                List<ReceiptItemData> items = new ArrayList<>();
                for (int j = 0; j < itemCount; j++) {
                    String productCode = productCodes.get((int)(Math.random() * productCodes.size()));
                    int qty = 1 + (int)(Math.random() * 5);
                    
                    // Get product price
                    String priceSql = String.format(
                        "SELECT selling_price FROM %s.products WHERE code = ?", schemaName
                    );
                    BigDecimal price = jdbcTemplate.queryForObject(priceSql, BigDecimal.class, productCode);
                    BigDecimal lineTotal = price.multiply(new BigDecimal(qty));
                    
                    items.add(new ReceiptItemData(productCode, "Product " + productCode, price, qty, lineTotal));
                    subtotal = subtotal.add(lineTotal);
                }
                
                BigDecimal tax = subtotal.multiply(new BigDecimal("0.10")); // 10% tax
                BigDecimal total = subtotal.add(tax);
                
                // Insert receipt
                String receiptSql = String.format(
                    "INSERT INTO %s.receipts (id, timestamp, subtotal, tax, tax_rate, discount, total, " +
                    "customer_name, payment_method, payment_status, cashier_id, cashier_name, is_voided, " +
                    "branch_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    schemaName
                );
                
                jdbcTemplate.update(receiptSql,
                    receiptId,
                    receiptDate,
                    subtotal,
                    tax,
                    new BigDecimal("10.00"),
                    BigDecimal.ZERO,
                    total,
                    "Customer " + (i + 1),
                    Math.random() > 0.3 ? "Cash" : "Card",
                    "PAID",
                    cashierEmail,
                    cashierEmail.split("@")[0],
                    false,
                    branchId,
                    receiptDate
                );
                
                // Insert receipt items
                for (ReceiptItemData item : items) {
                    String itemSql = String.format(
                        "INSERT INTO %s.receipt_items (id, receipt_id, product_code, product_name, price, qty, line_total) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        schemaName
                    );
                    jdbcTemplate.update(itemSql,
                        UUID.randomUUID().toString(),
                        receiptId,
                        item.productCode,
                        item.productName,
                        item.price,
                        item.qty,
                        item.lineTotal
                    );
                }
                
                totalItems += items.size();
            }
            totalReceipts += receiptsForBranch;
        }
        
        log.info("  ✓ Created {} receipts with {} items over 3 months", totalReceipts, totalItems);
    }

    private void createInventoryTransactions(String schemaName, List<String> branchIds, List<String> productCodes) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusMonths(3);
        
        int totalTransactions = 0;
        
        for (String branchId : branchIds) {
            // Generate ~50-80 inventory transactions per branch over 3 months
            int transactionsForBranch = 50 + (int)(Math.random() * 30);
            
            for (int i = 0; i < transactionsForBranch; i++) {
                String productCode = productCodes.get((int)(Math.random() * productCodes.size()));
                
                // Random date within 3 months
                LocalDateTime txDate = startDate.plusSeconds(
                    (long)(Math.random() * java.time.Duration.between(startDate, endDate).getSeconds())
                );
                
                // 80% stock in, 20% stock out/adjustment
                String txType = Math.random() > 0.2 ? "STOCK_IN" : "STOCK_OUT";
                int qtyChange = 10 + (int)(Math.random() * 50);
                
                // Get current stock
                String stockSql = String.format(
                    "SELECT stock FROM %s.products WHERE code = ?", schemaName
                );
                BigDecimal currentStock = jdbcTemplate.queryForObject(stockSql, BigDecimal.class, productCode);
                
                BigDecimal newStock;
                BigDecimal quantity;
                
                if ("STOCK_IN".equals(txType)) {
                    quantity = new BigDecimal(qtyChange);
                    newStock = currentStock.add(quantity);
                } else {
                    quantity = new BigDecimal(-Math.min(qtyChange, currentStock.intValue()));
                    newStock = currentStock.add(quantity);
                }
                
                // Insert inventory transaction
                String txSql = String.format(
                    "INSERT INTO %s.inventory_transactions (id, product_code, quantity, previous_stock, " +
                    "new_stock, unit_cost, total_cost, reference_number, supplier_name, notes, " +
                    "transaction_type, created_by, created_at, branch_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    schemaName
                );
                
                BigDecimal unitCost = new BigDecimal("5.00").add(new BigDecimal(Math.random() * 45));
                BigDecimal totalCost = unitCost.multiply(quantity.abs());
                
                jdbcTemplate.update(txSql,
                    UUID.randomUUID().toString(),
                    productCode,
                    quantity,
                    currentStock,
                    newStock,
                    unitCost,
                    totalCost,
                    "REF-" + (1000 + i),
                    "Supplier " + ((int)(Math.random() * 5) + 1),
                    txType + " transaction",
                    txType,
                    "system",
                    txDate,
                    branchId
                );
                
                // Update product stock
                String updateStockSql = String.format(
                    "UPDATE %s.products SET stock = ? WHERE code = ?", schemaName
                );
                jdbcTemplate.update(updateStockSql, newStock, productCode);
                
                totalTransactions++;
            }
        }
        
        log.info("  ✓ Created {} inventory transactions over 3 months", totalTransactions);
    }

    private static class ReceiptItemData {
        final String productCode;
        final String productName;
        final BigDecimal price;
        final int qty;
        final BigDecimal lineTotal;

        ReceiptItemData(String productCode, String productName, BigDecimal price, int qty, BigDecimal lineTotal) {
            this.productCode = productCode;
            this.productName = productName;
            this.price = price;
            this.qty = qty;
            this.lineTotal = lineTotal;
        }
    }

    // ==================== Data Classes ====================

    private static class TenantConfig {
        final String schemaName;
        final String companyName;
        final String adminEmail;
        final SubscriptionPlan subscriptionPlan;
        final List<UserData> users;
        final List<BranchData> branches;
        final List<ProductData> products;

        TenantConfig(String schemaName, String companyName, String adminEmail, SubscriptionPlan subscriptionPlan,
                     List<UserData> users, List<BranchData> branches, List<ProductData> products) {
            this.schemaName = schemaName;
            this.companyName = companyName;
            this.adminEmail = adminEmail;
            this.subscriptionPlan = subscriptionPlan;
            this.users = users;
            this.branches = branches;
            this.products = products;
        }
    }

    private static class UserData {
        final String email;
        final String firstName;
        final String lastName;
        final String role;

        UserData(String email, String firstName, String lastName, String role) {
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.role = role;
        }

        String getEmail() { return email; }
        String getFirstName() { return firstName; }
        String getLastName() { return lastName; }
        String getRole() { return role; }
    }

    private static class BranchData {
        private String id;
        final String name;
        final String code;
        final boolean isMain;
        final boolean isActive;
        final boolean canSell;

        BranchData(String name, String code, boolean isMain, boolean canSell) {
            this.name = name;
            this.code = code;
            this.isMain = isMain;
            this.isActive = true;
            this.canSell = canSell;
        }

        String getId() { return id; }
        void setId(String id) { this.id = id; }
        String getName() { return name; }
        String getCode() { return code; }
        boolean isMain() { return isMain; }
        boolean isActive() { return isActive; }
        boolean isCanSell() { return canSell; }
    }

    private static class ProductData {
        final String code;
        final String name;
        final BigDecimal price;
        final BigDecimal sellingPrice;
        final BigDecimal stock;
        final String description;

        ProductData(String code, String name, String price, String sellingPrice, 
                    String stock, String description) {
            this.code = code;
            this.name = name;
            this.price = new BigDecimal(price);
            this.sellingPrice = new BigDecimal(sellingPrice);
            this.stock = new BigDecimal(stock);
            this.description = description;
        }

        String getCode() { return code; }
        String getName() { return name; }
        BigDecimal getPrice() { return price; }
        BigDecimal getSellingPrice() { return sellingPrice; }
        BigDecimal getStock() { return stock; }
    }

    private static class UnitData {
        final String name;
        final String symbol;
        final String type;
        final boolean allowFractions;
        final int precision;

        UnitData(String name, String symbol, String type, boolean allowFractions, int precision) {
            this.name = name;
            this.symbol = symbol;
            this.type = type;
            this.allowFractions = allowFractions;
            this.precision = precision;
        }

        String getName() { return name; }
        String getSymbol() { return symbol; }
        String getType() { return type; }
        boolean isAllowFractions() { return allowFractions; }
        int getPrecision() { return precision; }
    }

    // ==================== Product Data Generators ====================

    private List<ProductData> getElectronicsProducts() {
        return Arrays.asList(
            new ProductData("LAPTOP001", "Laptop Dell Inspiron", "450.00", "599.99", "10", "15.6 inch laptop with Intel i5"),
            new ProductData("PHONE001", "iPhone 14 Pro", "899.00", "1099.00", "15", "Latest iPhone with 128GB"),
            new ProductData("TABLET001", "iPad Air", "499.00", "649.00", "8", "10.9 inch tablet with M1 chip"),
            new ProductData("MOUSE001", "Wireless Mouse Logitech", "15.00", "29.99", "50", "Ergonomic wireless mouse"),
            new ProductData("KEYBOARD001", "Mechanical Keyboard", "45.00", "79.99", "25", "RGB mechanical keyboard"),
            new ProductData("MONITOR001", "27-inch Monitor 4K", "280.00", "399.99", "12", "4K UHD monitor with HDR"),
            new ProductData("HEADPHONE001", "Sony WH-1000XM4", "250.00", "349.99", "20", "Noise cancelling headphones"),
            new ProductData("CHARGER001", "USB-C Charger 65W", "20.00", "34.99", "40", "Fast charging USB-C adapter"),
            new ProductData("CABLE001", "HDMI Cable 2m", "5.00", "12.99", "100", "High-speed HDMI 2.1 cable"),
            new ProductData("WEBCAM001", "Logitech Webcam HD", "35.00", "59.99", "30", "1080p HD webcam with mic"),
            new ProductData("SPEAKER001", "Bluetooth Speaker JBL", "80.00", "129.99", "18", "Portable waterproof speaker"),
            new ProductData("ROUTER001", "WiFi 6 Router", "120.00", "179.99", "15", "Dual-band gigabit router"),
            new ProductData("SSD001", "Samsung SSD 1TB", "85.00", "129.99", "22", "NVMe M.2 SSD 1TB"),
            new ProductData("RAM001", "Corsair RAM 16GB", "45.00", "69.99", "35", "DDR4 3200MHz RAM kit"),
            new ProductData("PRINTER001", "HP Laser Printer", "150.00", "229.99", "8", "Wireless monochrome printer"),
            new ProductData("INK001", "Printer Ink Cartridge", "25.00", "39.99", "60", "Black toner cartridge"),
            new ProductData("CASE001", "Laptop Case 15.6\"", "15.00", "29.99", "45", "Padded laptop carrying case"),
            new ProductData("STAND001", "Laptop Stand Aluminum", "20.00", "34.99", "28", "Adjustable laptop stand"),
            new ProductData("HUB001", "USB Hub 7-port", "18.00", "29.99", "33", "Powered USB 3.0 hub"),
            new ProductData("BATTERY001", "Power Bank 20000mAh", "25.00", "44.99", "40", "Fast charging power bank")
        );
    }

    private List<ProductData> getGroceryProducts() {
        return Arrays.asList(
            new ProductData("RICE001", "Basmati Rice 5kg", "4.50", "7.99", "50", "Premium long grain rice"),
            new ProductData("PASTA001", "Spaghetti 500g", "0.80", "1.49", "100", "Italian durum wheat pasta"),
            new ProductData("OIL001", "Olive Oil 1L", "4.00", "6.99", "40", "Extra virgin olive oil"),
            new ProductData("SUGAR001", "White Sugar 1kg", "1.20", "1.99", "80", "Refined white sugar"),
            new ProductData("FLOUR001", "All Purpose Flour 2kg", "1.50", "2.49", "60", "Wheat flour for baking"),
            new ProductData("MILK001", "Whole Milk 1L", "0.90", "1.49", "100", "Fresh whole milk"),
            new ProductData("EGGS001", "Large Eggs 12-pack", "2.50", "3.99", "50", "Free-range large eggs"),
            new ProductData("BREAD001", "White Bread Sliced", "1.00", "1.79", "40", "Fresh sliced white bread"),
            new ProductData("CHEESE001", "Cheddar Cheese 400g", "3.00", "4.99", "35", "Mature cheddar cheese"),
            new ProductData("YOGURT001", "Greek Yogurt 500g", "1.80", "2.99", "45", "Natural Greek yogurt"),
            new ProductData("CHICKEN001", "Chicken Breast 1kg", "5.00", "8.99", "30", "Fresh chicken breast fillets"),
            new ProductData("BEEF001", "Ground Beef 500g", "4.00", "6.99", "25", "Premium ground beef"),
            new ProductData("APPLE001", "Red Apples 1kg", "1.50", "2.49", "60", "Fresh red apples"),
            new ProductData("BANANA001", "Bananas 1kg", "0.90", "1.49", "80", "Fresh ripe bananas"),
            new ProductData("TOMATO001", "Tomatoes 1kg", "1.20", "1.99", "50", "Fresh vine tomatoes"),
            new ProductData("POTATO001", "Potatoes 2kg", "1.00", "1.79", "70", "White potatoes"),
            new ProductData("COFFEE001", "Ground Coffee 250g", "3.50", "5.99", "40", "Arabica ground coffee"),
            new ProductData("TEA001", "Black Tea 100 bags", "2.00", "3.49", "55", "Premium black tea bags"),
            new ProductData("JUICE001", "Orange Juice 1L", "1.50", "2.49", "45", "100% pure orange juice"),
            new ProductData("WATER001", "Mineral Water 6-pack", "1.80", "2.99", "100", "Natural mineral water")
        );
    }

    private List<ProductData> getFashionProducts() {
        return Arrays.asList(
            new ProductData("TSHIRT001", "Cotton T-Shirt White", "5.00", "9.99", "50", "100% cotton basic t-shirt"),
            new ProductData("JEANS001", "Slim Fit Jeans Blue", "20.00", "34.99", "40", "Classic slim fit denim jeans"),
            new ProductData("SHIRT001", "Formal Shirt White", "15.00", "24.99", "35", "Business formal cotton shirt"),
            new ProductData("DRESS001", "Summer Floral Dress", "25.00", "42.99", "25", "Light summer floral print dress"),
            new ProductData("JACKET001", "Denim Jacket Blue", "30.00", "49.99", "20", "Classic denim jacket"),
            new ProductData("SNEAKER001", "White Sneakers", "25.00", "44.99", "30", "Casual white sneakers"),
            new ProductData("BOOTS001", "Leather Boots Brown", "45.00", "74.99", "20", "Genuine leather boots"),
            new ProductData("SANDAL001", "Summer Sandals", "12.00", "19.99", "40", "Comfortable summer sandals"),
            new ProductData("BAG001", "Leather Handbag", "35.00", "59.99", "25", "Genuine leather handbag"),
            new ProductData("BELT001", "Leather Belt Black", "10.00", "16.99", "45", "Classic leather belt"),
            new ProductData("WALLET001", "Leather Wallet", "12.00", "19.99", "35", "Bifold genuine leather wallet"),
            new ProductData("SCARF001", "Silk Scarf", "15.00", "24.99", "30", "Elegant silk scarf"),
            new ProductData("HAT001", "Baseball Cap", "8.00", "14.99", "50", "Adjustable cotton cap"),
            new ProductData("SOCKS001", "Crew Socks 3-pack", "5.00", "8.99", "80", "Cotton crew socks pack"),
            new ProductData("UNDER001", "Boxer Briefs 3-pack", "8.00", "14.99", "60", "Cotton boxer briefs pack"),
            new ProductData("SHORTS001", "Cargo Shorts Khaki", "15.00", "24.99", "35", "Casual cargo shorts"),
            new ProductData("HOODIE001", "Pullover Hoodie Gray", "20.00", "34.99", "30", "Comfortable cotton hoodie"),
            new ProductData("COAT001", "Winter Coat Black", "60.00", "99.99", "15", "Warm winter padded coat"),
            new ProductData("SWEATER001", "Wool Sweater Navy", "25.00", "42.99", "25", "Soft wool knit sweater"),
            new ProductData("SUNG001", "UV Sunglasses", "10.00", "19.99", "40", "Polarized UV protection sunglasses")
        );
    }
}
