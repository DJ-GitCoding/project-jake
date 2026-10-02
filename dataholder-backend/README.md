# Test Data Holder Server

A Java/Spring Boot RDAP data holder server for testing the complete JADDAR agreement flow.

## Overview

This test data holder:
- Receives RDAP queries (domain, IP, ASN)
- Validates tokens against JADDAR's Keycloak via introspection
- Checks agreements and determines access level (0-3)
- Returns RDAP data based on the granted access level
- Supports manual verification workflow for high-level access
- Provides admin UI for reviewing pending requests

## Features

### Access Levels

| Level | Description | Data Included |
|-------|-------------|---------------|
| 0 | Minimal | Domain name, status only |
| 1 | Basic | + Registration dates, nameservers |
| 2 | Standard | + Registrar info, technical contacts |
| 3 | Full | + Registrant PII (name, email, address) |

### Endpoints

#### RDAP Endpoints (Standard)
- `GET /domain/{domain}?agreements=1,2,3` - Domain lookup
- `GET /ip/{ip}?agreements=1,2,3` - IP lookup
- `GET /autnum/{asn}?agreements=1,2,3` - ASN lookup

#### API Endpoints
- `GET /api/rdap/domain/{domain}` - API-style domain lookup
- `GET /api/rdap/ip/{ip}` - API-style IP lookup
- `GET /api/rdap/autnum/{asn}` - API-style ASN lookup
- `GET /api/rdap/status/{requestId}` - Check pending request status
- `GET /api/rdap/domains` - List available test domains

#### Admin Endpoints (Basic Auth)
- `GET /api/admin/pending-requests` - List pending requests
- `POST /api/admin/pending-requests/{id}/review` - Approve/deny request
- `GET /api/admin/agreement-levels` - List agreement access levels
- `POST /api/admin/agreement-levels` - Update agreement level
- `GET /api/admin/policy` - Get access policy
- `PUT /api/admin/policy` - Update access policy
- `GET /api/admin/audit-logs` - View audit logs

## Test Data

The server includes 10 pre-populated test domains:

1. `testdomain1.example` - Standard domain with all levels
2. `example-corp.example` - Corporate domain
3. `secure-bank.example` - High-security banking domain
4. `tech-startup.example` - Startup using Cloudflare
5. `government-agency.gov.example` - Government domain
6. `ecommerce-shop.example` - E-commerce domain
7. `university-edu.edu.example` - Educational domain
8. `media-news.example` - Media company domain
9. `healthcare-clinic.example` - Healthcare domain
10. `gaming-platform.example` - Gaming platform domain

Also includes sample IP network (`192.168.1.0/24`) and ASN (`AS12345`).

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/dataholder_db` | PostgreSQL connection URL |
| `KEYCLOAK_INTROSPECT_URL` | `http://keycloak:8080/realms/master/protocol/openid-connect/token/introspect` | Keycloak token introspection URL |
| `KEYCLOAK_CLIENT_ID` | `jaddar-app` | Client ID for introspection |
| `KEYCLOAK_CLIENT_SECRET` | `secret123` | Client secret |
| `DATAHOLDER_NAME` | `Test Data Holder` | Display name |
| `DATAHOLDER_ID` | `TDH-001` | Unique identifier |
| `DEFAULT_ACCESS_LEVEL` | `1` | Default access level when no agreements |
| `REQUIRE_AGREEMENT` | `true` | Whether agreements are required |
| `MANUAL_VERIFICATION_ENABLED` | `false` | Enable manual verification |
| `MANUAL_VERIFICATION_LEVEL` | `3` | Level at which manual verification kicks in |
| `ADMIN_USERNAME` | `admin` | Admin UI username |
| `ADMIN_PASSWORD` | `admin123` | Admin UI password |

## Running

### With Docker Compose

Add the service from `docker-compose-service.yml` to your main `docker-compose.yml`:

```bash
docker-compose up -d dataholder
```

### Standalone

```bash
# Build
mvn clean package

# Run
java -jar target/dataholder-server-1.0.0-SNAPSHOT.jar
```

## Usage Example

### Query with Agreement

```bash
# Get access token from Keycloak
TOKEN=$(curl -X POST "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=jaddar-app" \
  -d "username=testuser" \
  -d "password=password" | jq -r '.access_token')

# Query domain with agreements
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8082/domain/testdomain1.example?agreements=1,2"
```

### Response Format

```json
{
  "success": true,
  "queryType": "domain",
  "queryValue": "testdomain1.example",
  "accessLevel": 2,
  "data": {
    "objectClassName": "domain",
    "ldhName": "testdomain1.example",
    "handle": "D1234567-EX",
    "status": ["active"],
    "events": [...],
    "nameservers": [...],
    "entities": [...]
  },
  "timestamp": "2024-01-15T10:30:00Z",
  "source": "Test Data Holder",
  "agreementIds": [1, 2]
}
```

### Manual Verification Flow

When manual verification is required:

1. Initial request returns pending status:
```json
{
  "success": true,
  "data": {
    "status": "pending",
    "requestId": "abc-123-def",
    "message": "Your request requires manual verification",
    "pollUrl": "/api/rdap/status/abc-123-def",
    "expiresAt": "2024-01-16T10:30:00Z"
  }
}
```

2. Admin reviews and approves/denies via admin UI

3. Client polls status endpoint until approved/denied

## React Integration

Add these components to your React app:

1. Copy `TestDataHolder.jsx` to your components folder
2. Copy `DataHolderAdmin.jsx` for the admin interface
3. Add routes:

```jsx
import TestDataHolder from './components/TestDataHolder';
import DataHolderAdmin from './components/DataHolderAdmin';

<Route path="/test-dataholder" element={<TestDataHolder />} />
<Route path="/dataholder-admin" element={<DataHolderAdmin />} />
```

4. Add environment variable:
```
REACT_APP_DATAHOLDER_URL=http://localhost:8082
```

## Database Schema

The server uses Flyway for database migrations. Key tables:

- `rdap_domains` - Domain RDAP data at all access levels
- `rdap_ips` - IP network RDAP data
- `rdap_asns` - ASN RDAP data
- `agreement_access_levels` - Agreement to access level mapping
- `access_policies` - Access policy configuration
- `pending_requests` - Requests awaiting manual verification
- `request_audit_log` - All request history

## Agreement Access Level Mapping

Default configuration:

| Agreement ID | Name | Access Level |
|--------------|------|--------------|
| 1 | Basic RDAP Access | 1 |
| 2 | Standard RDAP Access | 2 |
| 3 | Full RDAP Access | 3 |

The server uses the **maximum** access level from all agreements in a request.

## Security

- Token validation via Keycloak introspection
- Admin endpoints protected with HTTP Basic Auth
- All requests logged to audit table
- CORS configured for local development

## Development

```bash
# Run with dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests
mvn test
```
