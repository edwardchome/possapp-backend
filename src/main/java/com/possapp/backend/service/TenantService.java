package com.possapp.backend.service;

import com.possapp.backend.dto.TenantDto;
import com.possapp.backend.dto.TenantRegistrationRequest;
import com.possapp.backend.entity.Tenant;
import com.possapp.backend.exception.TenantException;
import com.possapp.backend.repository.TenantRepository;
import com.possapp.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ============================================================================
 * TENANT SERVICE - Business/Tenant Management Business Logic
 * ============================================================================
 * 
 * This service handles all business logic related to tenants (businesses).
 * It manages the multi-tenant architecture where each business gets its own
 * isolated database schema.
 * 
 * KEY RESPONSIBILITIES:
 * 1. Tenant registration - Creates new business with full database setup
 * 2. Schema management - Creates/isolates each tenant's data
 * 3. Business settings - Update business information
 * 4. Admin user creation - Creates initial admin for new tenants
 * 
 * MULTI-TENANT ARCHITECTURE:
 * - Each tenant = One database schema
 * - Schemas are completely isolated
 * - Shared tables (tenants) in "public" schema
 * - All business data in tenant-specific schema
 * ============================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {
    
    /**
     * Repository for tenant CRUD operations.
     * Works with the "tenants" table in the public schema.
     */
    private final TenantRepository tenantRepository;
    
    /**
     * JdbcTemplate for low-level SQL operations.
     * Used for schema creation (not supported by JPA/Hibernate directly).
     */
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * DataSource for creating raw database connections.
     * Needed for schema creation and admin user setup.
     */
    private final DataSource dataSource;
    
    /**
     * Password encoder for hashing admin passwords.
     * Uses BCrypt for secure password storage.
     */
    private final PasswordEncoder passwordEncoder;
    
    /**
     * ==========================================================================
     * FIND TENANT BY SCHEMA NAME
     * ==========================================================================
     * Looks up a tenant by its schema name (business ID).
     * Used during login to validate tenant exists.
     * 
     * @param schemaName The business ID/schema name (e.g., "green_leaf_market")
     * @return Optional containing tenant if found
     * ==========================================================================
     */
    @Transactional(readOnly = true)
    public Optional<Tenant> findBySchemaName(String schemaName) {
        return tenantRepository.findBySchemaName(schemaName);
    }
    
    /**
     * ==========================================================================
     * FIND TENANT BY ID
     * ==========================================================================
     * Looks up a tenant by its UUID.
     * 
     * @param id The tenant UUID
     * @return Optional containing tenant if found
     * ==========================================================================
     */
    @Transactional(readOnly = true)
    public Optional<Tenant> findById(String id) {
        return tenantRepository.findById(id);
    }
    
    /**
     * ==========================================================================
     * CHECK IF SCHEMA EXISTS
     * ==========================================================================
     * Validates if a schema name is already taken.
     * Used during registration to prevent duplicates.
     * 
     * @param schemaName The schema name to check
     * @return true if schema exists, false otherwise
     * ==========================================================================
     */
    @Transactional(readOnly = true)
    public boolean existsBySchemaName(String schemaName) {
        return tenantRepository.existsBySchemaName(schemaName);
    }
    
    /**
     * ==========================================================================
     * CHECK IF ADMIN EMAIL EXISTS
     * ==========================================================================
     * Validates if an admin email is already registered.
     * Prevents duplicate admin accounts.
     * 
     * @param adminEmail The email to check
     * @return true if email exists, false otherwise
     * ==========================================================================
     */
    @Transactional(readOnly = true)
    public boolean existsByAdminEmail(String adminEmail) {
        return tenantRepository.existsByAdminEmail(adminEmail);
    }
    
    /**
     * ==========================================================================
     * REGISTER NEW TENANT (Complete Business Setup)
     * ==========================================================================
     * This is the main method for creating a new business account.
     * It performs the complete setup process:
     * 
     * FLOW:
     * 1. Validate schema name format (lowercase, alphanumeric + underscore)
     * 2. Check for duplicates (schema name, admin email)
     * 3. Create tenant record in public.tenants table
     * 4. Create database schema (e.g., "green_leaf_market")
     * 5. Create all tables in the new schema (users, products, receipts, etc.)
     * 6. Insert default data (General category, default units)
     * 7. Create admin user with provided email/password
     * 8. Return the created tenant DTO
     * 
     * @param request Registration request with company details
     * @return TenantDto with created tenant information
     * @throws TenantException if validation fails or setup errors occur
     * ==========================================================================
     */
    @Transactional
    public TenantDto registerTenant(TenantRegistrationRequest request) {
        // Step 1: Normalize and validate schema name
        // Convert to lowercase, replace invalid chars with underscore
        String schemaName = request.getSchemaName().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (!schemaName.matches("^[a-z][a-z0-9_]*$")) {
            throw new TenantException("Invalid schema name. Must start with letter and contain only letters, numbers, and underscores");
        }
        
        // Step 2: Check for duplicates
        if (tenantRepository.existsBySchemaName(schemaName)) {
            throw new TenantException("Tenant with this schema name already exists");
        }
        if (tenantRepository.existsByAdminEmail(request.getAdminEmail())) {
            throw new TenantException("Tenant with this admin email already exists");
        }
        
        // Step 3: Create tenant record in public schema
        // This stores the business info and links to the schema
        Tenant tenant = Tenant.builder()
            .companyName(request.getCompanyName())
            .schemaName(schemaName)
            .adminEmail(request.getAdminEmail())
            .contactPhone(request.getContactPhone())
            .address(request.getAddress())
            .subscriptionPlan(request.getSubscriptionPlan())
            .subscriptionExpiresAt(LocalDateTime.now().plusYears(1))
            .active(true)
            .build();
        
        tenant = tenantRepository.save(tenant);
        
        // Step 4-7: Create schema, tables, and admin user
        // This is done via raw JDBC as JPA doesn't support schema creation
        createTenantSchemaAndAdmin(schemaName, request.getAdminEmail(), request.getPassword());
        
        log.info("Successfully created tenant: {} with schema: {}", request.getCompanyName(), schemaName);
        
        // Step 8: Return DTO for response
        return mapToDto(tenant);
    }
    
    /**
     * ==========================================================================
     * CREATE TENANT SCHEMA AND ADMIN USER
     * ==========================================================================
     * This method creates the actual database infrastructure for a new tenant:
     * 
     * 1. Creates PostgreSQL schema (namespace for tables)
     * 2. Creates all necessary tables (users, products, receipts, etc.)
     * 3. Inserts default configuration (store settings, default units)
     * 4. Creates the admin user with the provided credentials
     * 
     * Uses raw JDBC because:
     * - JPA/Hibernate can't create schemas dynamically
     * - Need precise control over table creation
     * - Must set up tenant-specific defaults
     * 
     * @param schemaName The schema name to create
     * @param adminEmail Admin user's email
     * @param plainPassword Admin's plain text password (will be hashed)
     * @throws TenantException if database operations fail
     * ==========================================================================
     */
    private void createTenantSchemaAndAdmin(String schemaName, String adminEmail, String plainPassword) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Create the schema (namespace) for this tenant
            statement.executeUpdate("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            log.info("Created schema: {}", schemaName);
            
            // Create all tables within this schema
            createTenantTables(statement, schemaName);
            
            // Insert default store configuration
            statement.executeUpdate(String.format(
                "INSERT INTO %s.store_config (store_name, currency_code, currency_symbol) VALUES ('My Store', 'USD', '$')",
                schemaName
            ));
            
            // Create default main branch
            String mainBranchId = createDefaultMainBranch(connection, schemaName);
            log.info("Created main branch with ID: {} in schema: {}", mainBranchId, schemaName);
            
            // Create admin user with hashed password and assign to main branch
            String encodedPassword = passwordEncoder.encode(plainPassword);
            createAdminUserInSchema(connection, schemaName, adminEmail, encodedPassword, mainBranchId);
            
            log.info("Created admin user in schema: {}", schemaName);
            
        } catch (SQLException e) {
            log.error("Failed to create tenant schema: {}", schemaName, e);
            throw new TenantException("Failed to create tenant schema: " + e.getMessage());
        }
    }
    
    /**
     * ==========================================================================
     * CREATE DEFAULT MAIN BRANCH
     * ==========================================================================
     * Creates the default "Main Branch" for a new tenant.
     * Every tenant must have at least one branch, and this is created
     * automatically during tenant registration.
     * 
     * The main branch:
     * - Has is_main_branch = true
     * - Cannot be deleted (must always have at least one branch)
     * - Is used as default for all operations
     * 
     * @param connection Database connection
     * @param schemaName Target schema name
     * @return The ID of the created main branch
     * @throws SQLException if insert fails
     * ==========================================================================
     */
    private String createDefaultMainBranch(Connection connection, String schemaName) throws SQLException {
        String sql = String.format(
            "INSERT INTO %s.branches (id, name, code, is_main_branch, is_active, can_sell, created_at, updated_at) " +
            "VALUES (gen_random_uuid(), 'Main Branch', 'MAIN', true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
            "RETURNING id",
            schemaName
        );
        
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("id");
            }
            throw new SQLException("Failed to create main branch - no ID returned");
        }
    }
    
    /**
     * ==========================================================================
     * CREATE ADMIN USER IN TENANT SCHEMA
     * ==========================================================================
     * Inserts the admin user directly into the tenant's users table.
     * 
     * Note: email_verified is set to TRUE here because the registration
     * process already verified the email via the verification link.
     * 
     * @param connection Database connection
     * @param schemaName Target schema name
     * @param email Admin email
     * @param encodedPassword BCrypt hashed password
     * @param branchId The main branch ID to assign the admin to
     * @throws SQLException if insert fails
     * ==========================================================================
     */
    private void createAdminUserInSchema(Connection connection, String schemaName, String email, String encodedPassword, String branchId) throws SQLException {
        String sql = String.format(
            "INSERT INTO %s.users (id, email, password, role, is_active, email_verified, password_change_required, branch_id, created_at, updated_at) " +
            "VALUES (gen_random_uuid(), ?, ?, 'ADMIN', true, true, false, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            schemaName
        );
        
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, encodedPassword);
            ps.setString(3, branchId);
            int rowsAffected = ps.executeUpdate();
            log.info("Admin user insert affected {} rows in schema {} (email_verified=true, branch_id={})", 
                rowsAffected, schemaName, branchId);
        }
    }
    
    /**
     * ==========================================================================
     * CREATE TENANT TABLES
     * ==========================================================================
     * Creates all database tables for a new tenant.
     * 
     * TABLES CREATED:
     * 1. users - Staff/users of the business
     * 2. categories - Product categories
     * 3. products - Product catalog with unit of measure support
     * 4. receipts - Sales receipts/transactions
     * 5. receipt_items - Individual items in each receipt
     * 6. store_config - Business settings (receipt header/footer, tax rate, etc.)
     * 7. inventory_transactions - Stock additions history
     * 8. units_of_measure - Custom measurement units
     * 9. branches - Store locations for multi-branch support
     * 
     * Also creates indexes for performance and adds branch_id columns to
     * receipts, inventory_transactions, and users tables.
     * 
     * @param statement SQL statement object
     * @param schemaName Target schema name
     * @throws SQLException if table creation fails
     * ==========================================================================
     */
    private void createTenantTables(Statement statement, String schemaName) throws SQLException {
        // Users table - stores staff/users for this business
        statement.executeUpdate(String.format("""
            CREATE TABLE IF NOT EXISTS %s.users (
                id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                email VARCHAR(255) NOT NULL UNIQUE,
                password VARCHAR(255) NOT NULL,
                first_name VARCHAR(100),
                last_name VARCHAR(100),
                phone_number VARCHAR(50),
                role VARCHAR(50) NOT NULL DEFAULT 'USER',
                can_manage_products BOOLEAN NOT NULL DEFAULT false,
                can_manage_inventory BOOLEAN NOT NULL DEFAULT false,
                is_active BOOLEAN NOT NULL DEFAULT true,
                email_verified BOOLEAN NOT NULL DEFAULT false,
                email_verification_token VARCHAR(255),
                email_verification_expiry TIMESTAMP,
                password_change_required BOOLEAN NOT NULL DEFAULT false,
                password_reset_token VARCHAR(255),
                password_reset_expiry TIMESTAMP,
                permissions_version BIGINT NOT NULL DEFAULT 1,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                last_login_at TIMESTAMP
            )
            """, schemaName));
        
        // Categories table - for organizing products
        statement.executeUpdate(String.format("""
            CREATE TABLE IF NOT EXISTS %s.categories (
                id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                name VARCHAR(255) NOT NULL UNIQUE,
                description TEXT,
                display_order INTEGER DEFAULT 0,
                is_active BOOLEAN NOT NULL DEFAULT true,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """, schemaName));
        
        // Insert default General category
        statement.executeUpdate(String.format(
            "INSERT INTO %s.categories (id, name, description, display_order, is_active) VALUES (gen_random_uuid(), 'General', 'Default category for products', 0, true) ON CONFLICT (name) DO NOTHING",
            schemaName));
        
        // Products table - the product catalog
        // Supports fractional quantities (e.g., 1.5 kg of apples)
        statement.executeUpdate(String.format("""
            CREATE TABLE IF NOT EXISTS %s.products (
                code VARCHAR(100) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                price DECIMAL(10, 2) NOT NULL,
                selling_price DECIMAL(10, 2),
                stock DECIMAL(12, 3) NOT NULL DEFAULT 0,
                unit_of_measure VARCHAR(20) DEFAULT 'PCS',
                allow_decimal BOOLEAN NOT NULL DEFAULT false,
                min_quantity DECIMAL(10, 3) DEFAULT 1.000,
                step_quantity DECIMAL(10, 3) DEFAULT 1.000,
                category_id VARCHAR(36),
                description TEXT,
                image_url VARCHAR(500),
                cost_price DECIMAL(10, 2),
                min_stock_level DECIMAL(12, 3) DEFAULT 0,
                is_active BOOLEAN NOT NULL DEFAULT true,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_by VARCHAR(100),
                FOREIGN KEY (category_id) REFERENCES %s.categories(id)
            )
            """, schemaName, schemaName));
        
        // Receipts table - sales transactions
        statement.executeUpdate(String.format("""
            CREATE TABLE IF NOT EXISTS %s.receipts (
                id VARCHAR(100) PRIMARY KEY,
                timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                subtotal DECIMAL(10, 2) NOT NULL,
                tax DECIMAL(10, 2) NOT NULL DEFAULT 0,
                tax_rate DECIMAL(5, 2) DEFAULT 0,
                discount DECIMAL(10, 2) NOT NULL DEFAULT 0,
                total DECIMAL(10, 2) NOT NULL,
                customer_name VARCHAR(255),
                customer_email VARCHAR(255),
                customer_phone VARCHAR(50),
                payment_method VARCHAR(50) NOT NULL DEFAULT 'Cash',
                payment_status VARCHAR(50) NOT NULL DEFAULT 'PAID',
                notes TEXT,
                cashier_id VARCHAR(100),
                cashier_name VARCHAR(255),
                is_voided BOOLEAN NOT NULL DEFAULT false,
                voided_at TIMESTAMP,
                void_reason VARCHAR(500),
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """, schemaName));
        
        // Receipt items table - individual items in each receipt
        statement.executeUpdate(String.format("""
            CREATE TABLE IF NOT EXISTS %s.receipt_items (
                id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                receipt_id VARCHAR(100) NOT NULL REFERENCES %s.receipts(id) ON DELETE CASCADE,
                product_code VARCHAR(100) NOT NULL,
                product_name VARCHAR(255) NOT NULL,
                price DECIMAL(10, 2) NOT NULL,
                qty DECIMAL(15, 4) NOT NULL DEFAULT 0,
                line_total DECIMAL(10, 2) NOT NULL DEFAULT 0
            )
            """, schemaName, schemaName));
        
        // Store config table - business settings for receipts
        statement.executeUpdate(String.format("""
            CREATE TABLE IF NOT EXISTS %s.store_config (
                id SERIAL PRIMARY KEY,
                store_name VARCHAR(255) NOT NULL DEFAULT 'My Store',
                store_address TEXT,
                store_phone VARCHAR(50),
                store_email VARCHAR(255),
                receipt_header TEXT,
                receipt_footer TEXT DEFAULT 'Thank you for your business!',
                tax_rate DECIMAL(5, 2) DEFAULT 0,
                currency_code VARCHAR(10) DEFAULT 'USD',
                currency_symbol VARCHAR(10) DEFAULT '$',
                timezone VARCHAR(50) DEFAULT 'UTC',
                date_format VARCHAR(50) DEFAULT 'MMM dd, yyyy',
                time_format VARCHAR(50) DEFAULT 'HH:mm',
                enable_receipt_printing BOOLEAN NOT NULL DEFAULT true,
                enable_email_receipts BOOLEAN NOT NULL DEFAULT false,
                low_stock_alert_threshold INTEGER DEFAULT 10,
                receipt_printer_name VARCHAR(255),
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """, schemaName));
        
        // Inventory transactions table - tracks stock additions
        statement.executeUpdate(String.format("""
            CREATE TABLE IF NOT EXISTS %s.inventory_transactions (
                id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                product_code VARCHAR(100) NOT NULL,
                quantity DECIMAL(15, 4) NOT NULL,
                previous_stock DECIMAL(15, 4) NOT NULL,
                new_stock DECIMAL(15, 4) NOT NULL,
                unit_cost DECIMAL(10, 2),
                total_cost DECIMAL(10, 2),
                reference_number VARCHAR(255),
                supplier_name VARCHAR(255),
                notes TEXT,
                transaction_type VARCHAR(20) NOT NULL DEFAULT 'STOCK_IN',
                created_by VARCHAR(100),
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (product_code) REFERENCES %s.products(code)
            )
            """, schemaName, schemaName));
        
        // Units of measure table - custom measurement units
        statement.executeUpdate(String.format("""
            CREATE TABLE IF NOT EXISTS %s.units_of_measure (
                id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                name VARCHAR(255) NOT NULL,
                symbol VARCHAR(50) NOT NULL UNIQUE,
                type VARCHAR(20) NOT NULL,
                description TEXT,
                allow_fractions BOOLEAN NOT NULL DEFAULT false,
                default_precision INTEGER DEFAULT 0,
                is_active BOOLEAN NOT NULL DEFAULT true,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """, schemaName));
        
        // Insert default units of measure
        statement.executeUpdate(String.format("""
            INSERT INTO %s.units_of_measure (name, symbol, type, description, allow_fractions, default_precision, is_active) VALUES
                ('Pieces', 'pcs', 'COUNT', 'Individual items', false, 0, true),
                ('Kilogram', 'kg', 'WEIGHT', 'Weight in kilograms', true, 3, true),
                ('Gram', 'g', 'WEIGHT', 'Weight in grams', true, 0, true),
                ('Liter', 'LT', 'VOLUME', 'Volume in liters', true, 2, true),
                ('Milliliter', 'ml', 'VOLUME', 'Volume in milliliters', true, 0, true),
                ('Meter', 'm', 'LENGTH', 'Length in meters', true, 2, true),
                ('Centimeter', 'cm', 'LENGTH', 'Length in centimeters', true, 1, true),
                ('Square Meter', 'm²', 'AREA', 'Area in square meters', true, 2, true)
            ON CONFLICT (symbol) DO NOTHING
            """, schemaName));
        
        // Branches table - for multi-location support
        statement.executeUpdate(String.format("""
            CREATE TABLE IF NOT EXISTS %s.branches (
                id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                name VARCHAR(100) NOT NULL,
                code VARCHAR(20) UNIQUE,
                address VARCHAR(500),
                phone_number VARCHAR(50),
                email VARCHAR(255),
                is_main_branch BOOLEAN NOT NULL DEFAULT false,
                is_active BOOLEAN NOT NULL DEFAULT true,
                can_sell BOOLEAN NOT NULL DEFAULT true,
                tax_id VARCHAR(100),
                receipt_header TEXT,
                receipt_footer TEXT,
                manager_name VARCHAR(100),
                operating_hours VARCHAR(500),
                notes TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_by VARCHAR(36)
            )
            """, schemaName));
        
        // Create indexes for branches
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_branches_active ON %s.branches(is_active)", schemaName));
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_branches_main ON %s.branches(is_main_branch) WHERE is_main_branch = true", schemaName));
        
        // Add branch_id to existing tables for multi-location support
        // Receipts - track which branch made the sale
        statement.executeUpdate(String.format(
            "ALTER TABLE %s.receipts ADD COLUMN IF NOT EXISTS branch_id VARCHAR(36) REFERENCES %s.branches(id)",
            schemaName, schemaName));
        
        // Inventory transactions - track which branch received stock
        statement.executeUpdate(String.format(
            "ALTER TABLE %s.inventory_transactions ADD COLUMN IF NOT EXISTS branch_id VARCHAR(36) REFERENCES %s.branches(id)",
            schemaName, schemaName));
        
        // Users - track which branch user primarily works at (optional)
        statement.executeUpdate(String.format(
            "ALTER TABLE %s.users ADD COLUMN IF NOT EXISTS branch_id VARCHAR(36) REFERENCES %s.branches(id)",
            schemaName, schemaName));
        
        // Users - track currently active branch for the user
        statement.executeUpdate(String.format(
            "ALTER TABLE %s.users ADD COLUMN IF NOT EXISTS active_branch_id VARCHAR(36) REFERENCES %s.branches(id)",
            schemaName, schemaName));
        
        // Products - track which branch the product belongs to (optional - null means shared across branches)
        statement.executeUpdate(String.format(
            "ALTER TABLE %s.products ADD COLUMN IF NOT EXISTS branch_id VARCHAR(36) REFERENCES %s.branches(id)",
            schemaName, schemaName));
        
        // Create indexes for branch relationships
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_receipts_branch ON %s.receipts(branch_id)", schemaName));
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_inv_trans_branch ON %s.inventory_transactions(branch_id)", schemaName));
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_users_branch ON %s.users(branch_id)", schemaName));
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_users_active_branch ON %s.users(active_branch_id)", schemaName));
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_products_branch ON %s.products(branch_id)", schemaName));
        
        // Create indexes for better query performance
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_products_category ON %s.products(category_id)", schemaName));
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_receipts_timestamp ON %s.receipts(timestamp)", schemaName));
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_receipt_items_receipt ON %s.receipt_items(receipt_id)", schemaName));
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_inv_trans_product ON %s.inventory_transactions(product_code)", schemaName));
        statement.executeUpdate(String.format(
            "CREATE INDEX IF NOT EXISTS idx_inv_trans_date ON %s.inventory_transactions(created_at)", schemaName));
        
        log.info("Created tables in schema: {}", schemaName);
    }
    
    /**
     * ==========================================================================
     * DEACTIVATE TENANT (Soft Delete)
     * ==========================================================================
     * Deactivates a tenant by setting active=false.
     * The tenant's data remains but they cannot log in.
     * 
     * @param tenantId The tenant UUID to deactivate
     * @throws TenantException if tenant not found
     * ==========================================================================
     */
    @Transactional
    public void deactivateTenant(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantException("Tenant not found"));
        tenant.setActive(false);
        tenantRepository.save(tenant);
    }
    
    /**
     * ==========================================================================
     * ACTIVATE TENANT
     * ==========================================================================
     * Reactivates a previously deactivated tenant.
     * 
     * @param tenantId The tenant UUID to activate
     * @throws TenantException if tenant not found
     * ==========================================================================
     */
    @Transactional
    public void activateTenant(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantException("Tenant not found"));
        tenant.setActive(true);
        tenantRepository.save(tenant);
    }
    
    /**
     * ==========================================================================
     * CONVERT ENTITY TO DTO
     * ==========================================================================
     * Converts a Tenant entity to a TenantDto for API responses.
     * DTOs are safer to send to clients (don't expose internal fields).
     * 
     * @param tenant The Tenant entity
     * @return TenantDto for response
     * ==========================================================================
     */
    public TenantDto mapToDto(Tenant tenant) {
        return TenantDto.builder()
            .id(tenant.getId())
            .companyName(tenant.getCompanyName())
            .schemaName(tenant.getSchemaName())
            .adminEmail(tenant.getAdminEmail())
            .contactPhone(tenant.getContactPhone())
            .address(tenant.getAddress())
            .active(tenant.isActive())
            .subscriptionPlan(tenant.getSubscriptionPlan())
            .createdAt(tenant.getCreatedAt())
            .subscriptionExpiresAt(tenant.getSubscriptionExpiresAt())
            .build();
    }
    
    /**
     * ==========================================================================
     * GET CURRENT TENANT (Business Settings Page)
     * ==========================================================================
     * Gets the current tenant based on TenantContext.
     * TenantContext is set by TenantFilter from X-Tenant-ID header.
     * 
     * FLOW:
     * 1. Mobile app sends request with X-Tenant-ID header
     * 2. TenantFilter extracts tenant ID and sets TenantContext
     * 3. This method reads TenantContext to get current schema
     * 4. Looks up tenant in public.tenants table by schema name
     * 
     * @return TenantDto or null if not found
     * ==========================================================================
     */
    @Transactional(readOnly = true)
    public TenantDto getCurrentTenant() {
        String currentSchema = TenantContext.getCurrentTenant();
        if (currentSchema == null) {
            return null;
        }
        return tenantRepository.findBySchemaName(currentSchema)
            .map(this::mapToDto)
            .orElse(null);
    }
    
    /**
     * ==========================================================================
     * UPDATE CURRENT TENANT (Save Business Settings)
     * ==========================================================================
     * Updates the current tenant's business information.
     * Only updates allowed fields (companyName, contactPhone, address).
     * 
     * PROTECTED FIELDS (cannot change):
     * - schemaName: Would break all existing data references
     * - adminEmail: Requires verification process
     * - id: Immutable identifier
     * 
     * @param tenantDto DTO containing updated fields
     * @return Updated TenantDto
     * @throws TenantException if tenant not found or update fails
     * ==========================================================================
     */
    @Transactional
    public TenantDto updateCurrentTenant(TenantDto tenantDto) {
        String currentSchema = TenantContext.getCurrentTenant();
        if (currentSchema == null) {
            throw new TenantException("No tenant context found");
        }
        
        Tenant tenant = tenantRepository.findBySchemaName(currentSchema)
            .orElseThrow(() -> new TenantException("Tenant not found"));
        
        // Update only allowed fields
        if (tenantDto.getCompanyName() != null) {
            tenant.setCompanyName(tenantDto.getCompanyName());
        }
        if (tenantDto.getContactPhone() != null) {
            tenant.setContactPhone(tenantDto.getContactPhone());
        }
        if (tenantDto.getAddress() != null) {
            tenant.setAddress(tenantDto.getAddress());
        }
        
        tenant = tenantRepository.save(tenant);
        return mapToDto(tenant);
    }
}
