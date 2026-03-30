# Automatic Table Creation in Multi-Tenant SaaS Applications

> A comprehensive guide to the hybrid approach combining Flyway migrations with programmatic schema creation for PostgreSQL multi-tenant architectures.

## Table of Contents
1. [Overview](#overview)
2. [The Hybrid Approach](#the-hybrid-approach)
3. [Architecture Diagram](#architecture-diagram)
4. [How It Works](#how-it-works)
5. [Code Examples](#code-examples)
6. [Schema Updates for Existing Tenants](#schema-updates-for-existing-tenants)
7. [Comparison Table](#comparison-table)
8. [Key Takeaways](#key-takeaways)
9. [References](#references)

---

## Overview

This document explains how tables are created automatically in a **schema-per-tenant** multi-tenant architecture. Unlike traditional approaches where you run `schema.sql` once at application startup, this system dynamically creates complete database schemas whenever a new business (tenant) registers.

### Why Not Traditional schema.sql?

In a multi-tenant SaaS application, each business needs its own isolated database schema. Traditional `schema.sql` approaches assume a single database structure, which doesn't work when you have:

- 100+ businesses using your application
- Each business needs data isolation
- Dynamic tenant onboarding (self-service registration)
- Different default configurations per tenant

---

## The Hybrid Approach

This project uses a **two-tier system**:

| Approach | Purpose | When It Runs |
|----------|---------|--------------|
| **Flyway Migrations** | Public schema (shared tables) | Application startup |
| **Programmatic Creation** | Tenant schemas (per-business tables) | When new tenant registers |

### What Each Handles

**Flyway Manages:**
- `public.tenants` table (list of all businesses)
- Migration history tracking
- Schema evolution for the shared schema

**Programmatic Creation Handles:**
- Individual tenant schemas (e.g., `green_leaf_market`)
- All business tables (users, products, receipts, etc.)
- Default data insertion (categories, units of measure)
- Admin user creation

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    POSTGRESQL DATABASE                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  PUBLIC SCHEMA (Managed by Flyway)                          │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ tenants                                                ││
│  │ ─────────────────────────────────────────────────────  ││
│  │ id (UUID PK)           │ 550e8400-e29b-41d4-a716-...  ││
│  │ company_name           │ "Green Leaf Market"           ││
│  │ schema_name            │ "green_leaf_market"           ││
│  │ admin_email            │ "admin@greenleaf.com"         ││
│  │ is_active              │ true                          ││
│  │ subscription_plan      │ "FREE"                        ││
│  │ created_at             │ 2024-01-15 10:30:00           ││
│  └─────────────────────────────────────────────────────────┘│
│                    ↑                                         │
│                    │ References schema_name                 │
├────────────────────┼─────────────────────────────────────────┤
│                    │                                         │
│  GREEN_LEAF_MARKET SCHEMA (Created Programmatically)        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │   users      │  │  products    │  │    receipts      │   │
│  │ ──────────── │  │ ──────────── │  │ ──────────────── │   │
│  │ id (PK)      │  │ code (PK)    │  │ id (PK)          │   │
│  │ email (UQ)   │  │ name         │  │ timestamp        │   │
│  │ password     │  │ price        │  │ total            │   │
│  │ role         │  │ stock        │  │ payment_method   │   │
│  │ is_active    │  │ category_id  │  │ cashier_id       │   │
│  └──────────────┘  └──────────────┘  └──────────────────┘   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │  categories  │  │ store_config │  │ receipt_items    │   │
│  │ ──────────── │  │ ──────────── │  │ ──────────────── │   │
│  │ id (PK)      │  │ id (PK)      │  │ id (PK)          │   │
│  │ name (UQ)    │  │ store_name   │  │ receipt_id (FK)  │   │
│  │ description  │  │ tax_rate     │  │ product_code     │   │
│  └──────────────┘  └──────────────┘  │ qty              │   │
│  ┌─────────────────────────────────┐ │ line_total       │   │
│  │  inventory_transactions         │ └──────────────────┘   │
│  │ ─────────────────────────────── │                        │
│  │ id (PK)                         │  ┌──────────────────┐   │
│  │ product_code (FK)               │  │ units_of_measure │   │
│  │ quantity                        │  │ ──────────────── │   │
│  │ previous_stock                  │  │ id (PK)          │   │
│  │ new_stock                       │  │ name             │   │
│  │ transaction_type                │  │ symbol (UQ)      │   │
│  └─────────────────────────────────┘  │ type             │   │
│                                       │ allow_fractions  │   │
│                                       └──────────────────┘   │
│                                                              │
│  Created by: TenantService.createTenantSchemaAndAdmin()      │
│  Triggered: POST /api/v1/tenants/register                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## How It Works

### Step 1: Flyway Creates Public Schema

When the Spring Boot application starts, Flyway runs migration scripts:

```sql
-- V1__create_tenants_table.sql
CREATE TABLE IF NOT EXISTS public.tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(100) NOT NULL UNIQUE,  -- Links to tenant schema
    admin_email VARCHAR(255) NOT NULL UNIQUE,
    contact_phone VARCHAR(50),
    address TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'FREE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subscription_expires_at TIMESTAMP
);

-- Indexes for faster lookups
CREATE INDEX IF NOT EXISTS idx_tenants_schema_name ON public.tenants(schema_name);
CREATE INDEX IF NOT EXISTS idx_tenants_admin_email ON public.tenants(admin_email);

-- Auto-update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_tenants_updated_at
    BEFORE UPDATE ON public.tenants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

### Step 2: User Registers a New Business

When a user fills out the registration form and clicks **"Create Business Account"**:

```java
// POST /api/v1/tenants/register
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {
    
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TenantDto>> registerTenant(
            @RequestBody @Valid TenantRegistrationRequest request) {
        // This triggers the entire schema creation process
        TenantDto tenant = tenantService.registerTenant(request);
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }
}
```

### Step 3: TenantService.registerTenant() - The Orchestrator

```java
@Service
@RequiredArgsConstructor
public class TenantService {
    
    private final TenantRepository tenantRepository;
    private final DataSource dataSource;  // For raw JDBC
    private final PasswordEncoder passwordEncoder;
    
    @Transactional
    public TenantDto registerTenant(TenantRegistrationRequest request) {
        // 1. Validate schema name format
        // Must start with letter, only lowercase letters, numbers, underscores
        String schemaName = request.getSchemaName()
            .toLowerCase()
            .replaceAll("[^a-z0-9_]", "_");
        
        if (!schemaName.matches("^[a-z][a-z0-9_]*$")) {
            throw new TenantException(
                "Invalid schema name. Must start with letter and contain only " +
                "letters, numbers, and underscores"
            );
        }
        
        // 2. Check for duplicates
        if (tenantRepository.existsBySchemaName(schemaName)) {
            throw new TenantException("Tenant with this schema name already exists");
        }
        if (tenantRepository.existsByAdminEmail(request.getAdminEmail())) {
            throw new TenantException("Tenant with this admin email already exists");
        }
        
        // 3. Create tenant record in public schema
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
        
        // 4. 🔥 CREATE SCHEMA AND TABLES (Raw JDBC)
        createTenantSchemaAndAdmin(
            schemaName, 
            request.getAdminEmail(), 
            request.getPassword()
        );
        
        log.info("Successfully created tenant: {} with schema: {}", 
            request.getCompanyName(), schemaName);
        
        return mapToDto(tenant);
    }
}
```

### Step 4: Raw JDBC Schema Creation

```java
private void createTenantSchemaAndAdmin(
        String schemaName, 
        String adminEmail, 
        String plainPassword) {
    
    // Use try-with-resources for automatic cleanup
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
        
        // Step 1: Create the PostgreSQL schema (namespace)
        statement.executeUpdate("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        log.info("Created schema: {}", schemaName);
        
        // Step 2: Create all tables within this schema
        createTenantTables(statement, schemaName);
        
        // Step 3: Insert default store configuration
        statement.executeUpdate(String.format(
            "INSERT INTO %s.store_config (store_name, currency_code, currency_symbol) " +
            "VALUES ('My Store', 'USD', '$')",
            schemaName
        ));
        
        // Step 4: Create admin user with hashed password
        String encodedPassword = passwordEncoder.encode(plainPassword);
        createAdminUserInSchema(connection, schemaName, adminEmail, encodedPassword);
        
        log.info("Created admin user in schema: {}", schemaName);
        
    } catch (SQLException e) {
        log.error("Failed to create tenant schema: {}", schemaName, e);
        throw new TenantException("Failed to create tenant schema: " + e.getMessage());
    }
}
```

### Step 5: Creating Tables Dynamically

```java
private void createTenantTables(Statement statement, String schemaName) 
        throws SQLException {
    
    // ===== USERS TABLE =====
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
            permissions_version BIGINT NOT NULL DEFAULT 1,  -- JWT invalidation
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_login_at TIMESTAMP
        )
        """, schemaName));
    
    // ===== CATEGORIES TABLE =====
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
    
    // Insert default "General" category
    statement.executeUpdate(String.format(
        "INSERT INTO %s.categories (id, name, description, display_order, is_active) " +
        "VALUES (gen_random_uuid(), 'General', 'Default category for products', 0, true) " +
        "ON CONFLICT (name) DO NOTHING",
        schemaName
    ));
    
    // ===== PRODUCTS TABLE =====
    // Supports fractional quantities (e.g., 1.5 kg of apples)
    statement.executeUpdate(String.format("""
        CREATE TABLE IF NOT EXISTS %s.products (
            code VARCHAR(100) PRIMARY KEY,
            name VARCHAR(255) NOT NULL,
            price DECIMAL(10, 2) NOT NULL,
            selling_price DECIMAL(10, 2),
            stock DECIMAL(12, 3) NOT NULL DEFAULT 0,       -- 3 decimal places
            unit_of_measure VARCHAR(20) DEFAULT 'PCS',
            allow_decimal BOOLEAN NOT NULL DEFAULT false,  -- Allow 1.5 units?
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
    
    // ===== RECEIPTS TABLE =====
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
    
    // ===== RECEIPT ITEMS TABLE =====
    statement.executeUpdate(String.format("""
        CREATE TABLE IF NOT EXISTS %s.receipt_items (
            id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
            receipt_id VARCHAR(100) NOT NULL 
                REFERENCES %s.receipts(id) ON DELETE CASCADE,
            product_code VARCHAR(100) NOT NULL,
            product_name VARCHAR(255) NOT NULL,
            price DECIMAL(10, 2) NOT NULL,
            qty DECIMAL(15, 4) NOT NULL DEFAULT 0,        -- 4 decimal places
            line_total DECIMAL(10, 2) NOT NULL DEFAULT 0
        )
        """, schemaName, schemaName));
    
    // ===== STORE CONFIG TABLE =====
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
    
    // ===== INVENTORY TRANSACTIONS TABLE =====
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
    
    // ===== UNITS OF MEASURE TABLE =====
    statement.executeUpdate(String.format("""
        CREATE TABLE IF NOT EXISTS %s.units_of_measure (
            id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
            name VARCHAR(255) NOT NULL,
            symbol VARCHAR(50) NOT NULL UNIQUE,
            type VARCHAR(20) NOT NULL,                      -- WEIGHT, VOLUME, etc.
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
        INSERT INTO %s.units_of_measure 
            (name, symbol, type, description, allow_fractions, default_precision, is_active) 
        VALUES
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
    
    // ===== CREATE INDEXES FOR PERFORMANCE =====
    statement.executeUpdate(String.format(
        "CREATE INDEX IF NOT EXISTS idx_products_category ON %s.products(category_id)", 
        schemaName));
    statement.executeUpdate(String.format(
        "CREATE INDEX IF NOT EXISTS idx_receipts_timestamp ON %s.receipts(timestamp)", 
        schemaName));
    statement.executeUpdate(String.format(
        "CREATE INDEX IF NOT EXISTS idx_receipt_items_receipt ON %s.receipt_items(receipt_id)", 
        schemaName));
    statement.executeUpdate(String.format(
        "CREATE INDEX IF NOT EXISTS idx_inv_trans_product ON %s.inventory_transactions(product_code)", 
        schemaName));
    statement.executeUpdate(String.format(
        "CREATE INDEX IF NOT EXISTS idx_inv_trans_date ON %s.inventory_transactions(created_at)", 
        schemaName));
    
    log.info("Created tables in schema: {}", schemaName);
}
```

---

## Schema Updates for Existing Tenants

**The Challenge:** You have 100 businesses using your app. You need to add a `permissions_version` column to ALL their `users` tables for a new feature. How?

**The Solution:** Dynamic migration using PostgreSQL functions:

```sql
-- V13__add_permissions_version.sql

-- Create a function that adds column to ONE schema (only if needed)
CREATE OR REPLACE FUNCTION add_permissions_version_to_schema(schema_name text)
RETURNS void AS $$
BEGIN
    -- Check if the schema exists
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = $1) THEN
        
        -- Check if the users table exists in this schema
        IF EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = $1 AND table_name = 'users'
        ) THEN
            
            -- Check if the column doesn't already exist
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = $1 
                AND table_name = 'users' 
                AND column_name = 'permissions_version'
            ) THEN
                -- Add the column dynamically using EXECUTE
                EXECUTE format(
                    'ALTER TABLE %I.users ADD COLUMN permissions_version BIGINT NOT NULL DEFAULT 1', 
                    $1
                );
                
                -- Add documentation
                EXECUTE format(
                    'COMMENT ON COLUMN %I.users.permissions_version IS ''Version number that increments when user role or permissions change. Used to invalidate JWT tokens.''', 
                    $1
                );
            END IF;
        END IF;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Apply to ALL tenant schemas (exclude system schemas)
DO $$
DECLARE
    schema_record RECORD;
    has_tenant_schemas BOOLEAN := false;
BEGIN
    -- Check if any tenant schemas exist
    SELECT EXISTS (
        SELECT 1 FROM information_schema.schemata 
        WHERE schema_name NOT IN ('public', 'information_schema')
        AND schema_name NOT LIKE 'pg_%'
    ) INTO has_tenant_schemas;
    
    -- Only process if tenant schemas exist
    IF has_tenant_schemas THEN
        FOR schema_record IN 
            SELECT schema_name 
            FROM information_schema.schemata 
            WHERE schema_name NOT IN ('public', 'information_schema')
            AND schema_name NOT LIKE 'pg_%'
        LOOP
            PERFORM add_permissions_version_to_schema(schema_record.schema_name);
        END LOOP;
    END IF;
END $$;

-- Clean up the function
DROP FUNCTION IF EXISTS add_permissions_version_to_schema(text);
```

### Why This Approach Works:

1. **Idempotent** - Can run multiple times safely
2. **Conditional** - Only affects schemas/tables that exist and don't have the column
3. **Automatic** - Runs on every startup via Flyway
4. **Handles Edge Cases** - Works with 0 tenants or 10,000 tenants

---

## Comparison Table

| Aspect | Traditional schema.sql | Programmatic (This Approach) |
|--------|----------------------|------------------------------|
| **When tables created** | App startup | When tenant registers |
| **Schema flexibility** | Fixed schema per app | Dynamic schemas per tenant |
| **Multi-tenancy support** | Single database | Schema-per-tenant isolation |
| **Default data insertion** | `data.sql` file | Programmatic with custom logic |
| **Schema evolution** | Manual SQL scripts | Flyway + dynamic functions |
| **Rollback capability** | Limited | Can drop entire schema if needed |
| **Testing** | One database setup | Clean schema per test tenant |
| **Complexity** | Simple | More complex but powerful |
| **Data isolation** | Application-level | Database-level (true isolation) |

---

## Key Takeaways

### 1. **Programmatic Creation = Full Control**
```java
// You can:
- Set different defaults per tenant
- Apply custom configurations
- Log creation events
- Run validations
- Send notifications
```

### 2. **Use Raw JDBC for Schema Operations**
```java
// JPA/Hibernate cannot create schemas dynamically
// Raw JDBC gives you precise control:
Statement statement = connection.createStatement();
statement.executeUpdate("CREATE SCHEMA IF NOT EXISTS " + schemaName);
statement.executeUpdate(String.format("CREATE TABLE %s.users (...)", schemaName));
```

### 3. **Flyway Handles the Shared Schema**
```sql
-- The public schema (tenants table) uses traditional migrations
-- This is your "registry" of all businesses
CREATE TABLE public.tenants (...)
```

### 4. **Dynamic Migrations for Schema Evolution**
```sql
-- When adding columns to ALL tenants, use PostgreSQL functions
-- that iterate over schemas and conditionally apply changes
```

### 5. **Trade-offs to Consider**

| Pros | Cons |
|------|------|
| True data isolation | More complex than single schema |
| Dynamic onboarding | Need to handle schema migrations |
| Custom defaults per tenant | More database connections |
| Easy tenant deletion (drop schema) | Requires more disk space |

### 6. **Security Benefits**
- Each tenant's data is **completely isolated** in its own schema
- You cannot accidentally query another tenant's data
- Schema-level permissions can be applied
- Easy to export a single tenant's data (pg_dump -n schema_name)

---

## References

### Key Files in This Project

| File | Purpose |
|------|---------|
| `TenantService.java` | Business logic for tenant registration and schema creation |
| `V1__create_tenants_table.sql` | Flyway migration for public.tenants table |
| `V13__add_permissions_version.sql` | Dynamic migration example |
| `TenantContext.java` | ThreadLocal storage for current tenant |
| `TenantFilter.java` | Extracts X-Tenant-ID header from requests |

### External Resources

1. **PostgreSQL Schemas:** https://www.postgresql.org/docs/current/ddl-schemas.html
2. **Flyway Migrations:** https://documentation.red-gate.com/flyway
3. **Multi-Tenant Architectures:** https://docs.microsoft.com/en-us/azure/sql-database/saas-tenancy-app-design-patterns
4. **Spring JDBC:** https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#jdbc

---

## Quick Reference: Creating a New Tenant

```
┌────────────────────────────────────────────────────────────┐
│  USER ACTION: "Create Business Account"                    │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────┐
│  1. POST /api/v1/tenants/register                          │
│     Body: { companyName, schemaName, adminEmail, ... }    │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────┐
│  2. TenantService.registerTenant()                         │
│     - Validate schema name                                 │
│     - Check for duplicates                                 │
│     - Save tenant record in public.tenants                │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────┐
│  3. TenantService.createTenantSchemaAndAdmin()             │
│     - CREATE SCHEMA green_leaf_market                     │
│     - Create 8 tables (users, products, receipts, etc.)   │
│     - Insert default data                                  │
│     - Create admin user with BCrypt password              │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────┐
│  4. Return TenantDto to client                             │
│     { id, companyName, schemaName, adminEmail, ... }      │
└────────────────────────────────────────────────────────────┘
```

---

*Document created for reference. Last updated: 2026-03-19*
