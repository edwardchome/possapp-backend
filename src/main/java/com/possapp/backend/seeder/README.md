# Database Seeder

This module provides database seeding functionality for development and testing environments.

## Overview

The seeder creates the following test data:
- **3 Tenants**: TechNova Electronics, FreshMart Grocery, StyleHub Fashion
- **3 Users per tenant**: Admin, Manager, Cashier
- **3 Branches per tenant**: Main Store + 2 additional branches
- **8 Categories per tenant**: General, Electronics, Accessories, etc.
- **8 Units of Measure per tenant**: Pieces, Kilograms, Liters, etc.
- **20 Products per tenant**: Tailored to each business type

## Usage

### Option 1: Automatic Seeding on Startup (Recommended for fresh installs)

Activate the `seed` profile:

```bash
# Using command line
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed

# Or with environment variable
export SPRING_PROFILES_ACTIVE=seed
./mvnw spring-boot:run
```

### Option 2: REST API Endpoint (For existing running instances)

With the application running in dev/test mode:

```bash
# Check seeder status/info
curl http://localhost:8080/api/seed

# Run the seeder
curl -X POST http://localhost:8080/api/seed
```

### Option 3: Programmatic Access

```java
@Autowired
private DatabaseSeeder databaseSeeder;

// Call directly
public void seed() {
    databaseSeeder.seedAll();
}
```

## Login Credentials

After seeding, you can log in with any of these accounts:

| Tenant | Email | Password | Role |
|--------|-------|----------|------|
| TechNova | admin@technova.com | password123 | ADMIN |
| TechNova | manager@technova.com | password123 | MANAGER |
| TechNova | cashier@technova.com | password123 | USER |
| FreshMart | admin@freshmart.com | password123 | ADMIN |
| FreshMart | manager@freshmart.com | password123 | MANAGER |
| FreshMart | cashier@freshmart.com | password123 | USER |
| StyleHub | admin@stylehub.com | password123 | ADMIN |
| StyleHub | manager@stylehub.com | password123 | MANAGER |
| StyleHub | cashier@stylehub.com | password123 | USER |

## Tenant Details

### TechNova Electronics (technova)
- **Type**: Electronics store
- **Products**: Laptops, phones, accessories, peripherals
- **Branches**: Main Store, Downtown Branch, Warehouse

### FreshMart Grocery (freshmart)
- **Type**: Grocery store
- **Products**: Rice, pasta, dairy, produce, beverages
- **Branches**: Main Store, Express Branch, Storage Facility

### StyleHub Fashion (stylehub)
- **Type**: Clothing/fashion store
- **Products**: T-shirts, jeans, dresses, shoes, accessories
- **Branches**: Mall Store, Street Shop, Online Warehouse

## Security Note

⚠️ **The seeder is only available in the following profiles:**
- `dev`
- `development`
- `test`
- `seed`

The seeder endpoints and beans are **NOT** available in production (`prod`) profile.

## Resetting Data

To reset and re-seed:

1. Drop the tenant schemas:
```sql
DROP SCHEMA IF EXISTS technova CASCADE;
DROP SCHEMA IF EXISTS freshmart CASCADE;
DROP SCHEMA IF EXISTS stylehub CASCADE;
DELETE FROM public.tenants WHERE schema_name IN ('technova', 'freshmart', 'stylehub');
```

2. Restart the application with the seed profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
```

## Troubleshooting

### "Tenant already exists" warning
If a tenant already exists, the seeder will skip it. To re-seed, you must manually drop the tenant schema first.

### Schema doesn't exist errors
Ensure your database user has permission to create schemas and that PostgreSQL is running.

### LazyInitializationException
This is handled automatically by the seeder - it uses JDBC directly for tenant-specific operations.
