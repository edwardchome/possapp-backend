# PossApp Backend API

A multi-tenant Spring Boot backend API for the PossApp mobile POS application.

## Features

- **Multi-Tenant Architecture**: Schema-based tenant isolation using PostgreSQL
- **JWT Authentication**: Secure token-based authentication
- **POS Operations**: Product management, sales processing, receipt generation
- **Inventory Management**: Stock tracking, low stock alerts
- **Receipt History**: Complete sales history with search and filtering
- **Store Configuration**: Customizable store settings per tenant

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Multi-Tenant API                         │
├─────────────────────────────────────────────────────────────┤
│  Public Schema                    Tenant Schema             │
│  ───────────────                  ─────────────             │
│  • tenants (registry)             • users                   │
│                                   • products                │
│                                   • receipts                │
│                                   • receipt_items           │
│                                   • store_config            │
└─────────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- Java 21
- Maven 3.9+
- Docker (for PostgreSQL)

### 1. Start PostgreSQL

```bash
docker-compose up -d postgres
```

### 2. Build and Run

```bash
./mvnw clean install
./mvnw spring-boot:run
```

Or use the dev profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Access the API

- API Base URL: `http://localhost:8080`
- Health Check: `http://localhost:8080/actuator/health`

## API Endpoints

### Authentication
```
POST /api/v1/auth/login              - Login
POST /api/v1/auth/register           - Register (in tenant)
POST /api/v1/auth/refresh            - Refresh token
GET  /api/v1/auth/validate           - Validate session
POST /api/v1/auth/logout             - Logout
GET  /api/v1/auth/profile            - Get user profile
PUT  /api/v1/auth/profile            - Update profile
DELETE /api/v1/auth/account          - Delete account
```

### Tenant Management
```
POST /api/v1/tenants/register        - Register new tenant
GET  /api/v1/tenants/{schemaName}    - Get tenant info
POST /api/v1/tenants/{id}/activate   - Activate tenant
POST /api/v1/tenants/{id}/deactivate - Deactivate tenant
```

### Products
```
GET    /api/v1/products              - List all products
GET    /api/v1/products/{code}       - Get product by code
POST   /api/v1/products              - Create product
PUT    /api/v1/products/{code}       - Update product
DELETE /api/v1/products/{code}       - Delete product
PATCH  /api/v1/products/{code}/stock - Update stock
POST   /api/v1/products/{code}/stock/adjust - Adjust stock
GET    /api/v1/products/search?query={query} - Search products
GET    /api/v1/products/categories   - Get all categories
GET    /api/v1/products/low-stock    - Get low stock products
```

### Sales & Receipts
```
POST /api/v1/sales                   - Process sale
GET  /api/v1/receipts                - List receipts
GET  /api/v1/receipts/{id}           - Get receipt by ID
POST /api/v1/receipts/{id}/void      - Void receipt
GET  /api/v1/receipts/search?query={query} - Search receipts
GET  /api/v1/receipts/date-range?startDate={}&endDate={} - Filter by date
GET  /api/v1/analytics/sales-report  - Sales analytics
```

### Store Configuration
```
GET  /api/v1/store/config            - Get store config
PUT  /api/v1/store/config            - Update store config
```

## Multi-Tenant Usage

All tenant-scoped requests must include the `X-Tenant-ID` header:

```bash
# Example: Login to a tenant
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: mystore" \
  -d '{"email":"admin@example.com","password":"password"}'

# Example: List products in a tenant
curl -X GET http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer <token>" \
  -H "X-Tenant-ID: mystore"
```

## Registering a New Tenant

```bash
curl -X POST http://localhost:8080/api/v1/tenants/register \
  -H "Content-Type: application/json" \
  -d '{
    "companyName": "My Store",
    "schemaName": "mystore",
    "adminEmail": "admin@mystore.com",
    "password": "securepassword",
    "contactPhone": "+1234567890",
    "address": "123 Main St"
  }'
```

This creates:
1. A tenant record in the `public.tenants` table
2. A new schema named `mystore`
3. All necessary tables in the tenant schema
4. An admin user in the tenant

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_USER` | PostgreSQL username | `possapp` |
| `DB_PASSWORD` | PostgreSQL password | `possapp123` |
| `JWT_SECRET` | JWT signing secret | (default in config) |
| `SERVER_PORT` | Server port | `8080` |

### JWT Secret Generation

Generate a secure JWT secret:
```bash
openssl rand -base64 64
```

Update `application.yml`:
```yaml
jwt:
  secret: ${JWT_SECRET:your-generated-secret}
```

## Database Schema

### Public Schema (Tenant Registry)

The `tenants` table stores metadata about each tenant and their associated schema.

### Tenant Schemas

Each tenant gets their own schema with the following tables:
- `users` - User accounts
- `products` - Product catalog
- `receipts` - Sales receipts
- `receipt_items` - Receipt line items
- `store_config` - Store settings

## Security

- **Password Encryption**: BCrypt with strength 10
- **JWT Tokens**: HS512 algorithm, 24h expiration
- **Tenant Isolation**: Schema-based data separation
- **CORS**: Configured for mobile app access

## Development

### Running Tests
```bash
./mvnw test
```

### Code Formatting
```bash
./mvnw spotless:apply
```

### Database Console (PGAdmin)
```bash
docker-compose --profile tools up -d
# Access at http://localhost:5050
```

## Mobile App Integration

The backend is designed to work with the Flutter PossApp mobile app. Update the Flutter app's `MockApiService` to point to this backend:

```dart
// In lib/services/mock_api_service.dart
static const String baseUrl = 'http://your-server:8080/api/v1';
```

Add the tenant header to all requests:
```dart
// Add to HTTP client configuration
headers: {
  'Content-Type': 'application/json',
  'Authorization': 'Bearer $token',
  'X-Tenant-ID': tenantId,
}
```

## License

MIT License - See LICENSE file for details
