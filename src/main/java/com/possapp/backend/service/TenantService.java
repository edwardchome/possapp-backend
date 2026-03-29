package com.possapp.backend.service;

import com.possapp.backend.dto.TenantDto;
import com.possapp.backend.dto.TenantRegistrationRequest;
import com.possapp.backend.entity.Tenant;
import com.possapp.backend.exception.TenantException;
import com.possapp.backend.repository.TenantRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {
    
    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    
    @Transactional(readOnly = true)
    public Optional<Tenant> findBySchemaName(String schemaName) {
        return tenantRepository.findBySchemaName(schemaName);
    }
    
    @Transactional(readOnly = true)
    public Optional<Tenant> findById(String id) {
        return tenantRepository.findById(id);
    }
    
    @Transactional(readOnly = true)
    public boolean existsBySchemaName(String schemaName) {
        return tenantRepository.existsBySchemaName(schemaName);
    }
    
    @Transactional(readOnly = true)
    public boolean existsByAdminEmail(String adminEmail) {
        return tenantRepository.existsByAdminEmail(adminEmail);
    }
    
    @Transactional
    public TenantDto registerTenant(TenantRegistrationRequest request) {
        // Validate schema name format
        String schemaName = request.getSchemaName().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (!schemaName.matches("^[a-z][a-z0-9_]*$")) {
            throw new TenantException("Invalid schema name. Must start with letter and contain only letters, numbers, and underscores");
        }
        
        // Check if tenant already exists
        if (tenantRepository.existsBySchemaName(schemaName)) {
            throw new TenantException("Tenant with this schema name already exists");
        }
        if (tenantRepository.existsByAdminEmail(request.getAdminEmail())) {
            throw new TenantException("Tenant with this admin email already exists");
        }
        
        // Create tenant record in public schema
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
        
        // Create the tenant schema, tables, and admin user
        createTenantSchemaAndAdmin(schemaName, request.getAdminEmail(), request.getPassword());
        
        log.info("Successfully created tenant: {} with schema: {}", request.getCompanyName(), schemaName);
        
        return mapToDto(tenant);
    }
    
    private void createTenantSchemaAndAdmin(String schemaName, String adminEmail, String plainPassword) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Create schema
            statement.executeUpdate("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            log.info("Created schema: {}", schemaName);
            
            // Create tables in the tenant schema
            createTenantTables(statement, schemaName);
            
            // Insert default store config
            statement.executeUpdate(String.format(
                "INSERT INTO %s.store_config (store_name, currency_code, currency_symbol) VALUES ('My Store', 'USD', '$')",
                schemaName
            ));
            
            // Create admin user directly using JDBC to ensure correct schema
            // Note: email_verified is set to FALSE - user must verify email
            String encodedPassword = passwordEncoder.encode(plainPassword);
            createAdminUserInSchema(connection, schemaName, adminEmail, encodedPassword);
            
            log.info("Created admin user in schema: {}", schemaName);
            
        } catch (SQLException e) {
            log.error("Failed to create tenant schema: {}", schemaName, e);
            throw new TenantException("Failed to create tenant schema: " + e.getMessage());
        }
    }
    
    private void createAdminUserInSchema(Connection connection, String schemaName, String email, String encodedPassword) throws SQLException {
        String sql = String.format(
            "INSERT INTO %s.users (id, email, password, role, is_active, email_verified, password_change_required, created_at, updated_at) " +
            "VALUES (gen_random_uuid(), ?, ?, 'ADMIN', true, true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            schemaName
        );
        
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, encodedPassword);
            int rowsAffected = ps.executeUpdate();
            log.info("Admin user insert affected {} rows in schema {} (email_verified=true)", rowsAffected, schemaName);
        }
    }
    
    private void createTenantTables(Statement statement, String schemaName) throws SQLException {
        // Users table
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
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                last_login_at TIMESTAMP
            )
            """, schemaName));
        
        // Categories table
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
        
        // Products table with category reference and unit of measurement support
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
        
        // Receipts table
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
        
        // Receipt items table with fractional quantity support
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
        
        // Store config table
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
        
        // Inventory transactions table with BigDecimal support
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
        
        // Create indexes
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
    
    @Transactional
    public void deactivateTenant(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantException("Tenant not found"));
        tenant.setActive(false);
        tenantRepository.save(tenant);
    }
    
    @Transactional
    public void activateTenant(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantException("Tenant not found"));
        tenant.setActive(true);
        tenantRepository.save(tenant);
    }
    
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
}
