# Requestor Manager

A Spring Boot REST API server for managing agreements with independent JWT authentication and role-based access control (RBAC).

## Features

- **CRUD Operations** for Agreements, Requestor Groups, and Users
- **Independent JWT Authentication** (does not use Keycloak)
- **Role-Based Access Control** with 4 user levels:
  - **MASTER**: Full system access, can manage all users and data
  - **ADMIN**: Can manage requestor groups, users, and agreements
  - **REQUESTOR_GROUP_ADMIN**: Can manage users and agreements within their group
  - **REQUESTOR_GROUP_USER**: Can view data within their group
- **Swagger/OpenAPI Documentation**
- **PostgreSQL Database**
- **Docker Support** for running alongside your existing compose setup

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Security with JWT
- Spring Data JPA
- PostgreSQL
- JWT (jjwt 0.12.3)
- Swagger/OpenAPI (springdoc)
- Lombok
- Maven
- Docker

## Project Structure

```
agreement-server/
├── Dockerfile
├── docker-compose.agreement.yml    # Snippet to add to your compose
├── docker-compose.full.yml         # Complete compose with Requestor Manager
├── init-multiple-dbs.sh           # Script to create multiple DBs
├── pom.xml
├── .env.example
└── src/main/
    ├── java/com/agreements/
    │   ├── AgreementServerApplication.java
    │   ├── config/
    │   │   ├── DataInitializer.java
    │   │   ├── OpenApiConfig.java
    │   │   └── SecurityConfig.java
    │   ├── controller/
    │   │   ├── AgreementController.java
    │   │   ├── AuthController.java
    │   │   ├── RequestorGroupController.java
    │   │   └── UserController.java
    │   ├── dto/
    │   │   ├── AgreementDto.java
    │   │   ├── ApiResponse.java
    │   │   ├── AuthDto.java
    │   │   ├── RequestorGroupDto.java
    │   │   └── UserDto.java
    │   ├── entity/
    │   │   ├── Agreement.java
    │   │   ├── RequestorGroup.java
    │   │   └── User.java
    │   ├── enums/
    │   │   └── UserType.java
    │   ├── exception/
    │   │   ├── CustomExceptions.java
    │   │   └── GlobalExceptionHandler.java
    │   ├── repository/
    │   │   ├── AgreementRepository.java
    │   │   ├── RequestorGroupRepository.java
    │   │   └── UserRepository.java
    │   ├── security/
    │   │   ├── JwtAuthenticationFilter.java
    │   │   └── JwtService.java
    │   └── service/
    │       ├── AgreementService.java
    │       ├── AuthService.java
    │       ├── RequestorGroupService.java
    │       └── UserService.java
    └── resources/
        └── application.yml
```

## Integration with Existing Docker Compose

### Option 1: Add to Existing Compose

1. **Copy the `agreement-server` folder** to your project root (same level as `backend`, `frontend`)

2. **Add environment variables** to your `.env` file:
   ```bash
   # Requestor Manager
   REQUESTOR_MANAGER_SERVER_PORT=8081
   REQUESTOR_MANAGER_DB_NAME=agreement_db
   REQUESTOR_MANAGER_JWT_SECRET=myVeryLongAndSecureSecretKeyForJWTTokenGeneration256Bits
   REQUESTOR_MANAGER_MASTER_EMAIL=master@agreements.com
   REQUESTOR_MANAGER_MASTER_PASSWORD=Master@123
   ```

3. **Copy `init-multiple-dbs.sh`** to your project root and make it executable:
   ```bash
   chmod +x init-multiple-dbs.sh
   ```

4. **Add to your PostgreSQL service** in docker-compose.yml:
   ```yaml
   postgres:
     environment:
       - POSTGRES_MULTIPLE_DATABASES=${POSTGRES_DB},${REQUESTOR_MANAGER_DB_NAME:-agreement_db}
     volumes:
       - ./init-multiple-dbs.sh:/docker-entrypoint-initdb.d/init-multiple-dbs.sh
   ```

5. **Add the agreement-backend service** from `docker-compose.agreement.yml` to your services

### Option 2: Use Complete Compose File

Replace your `docker-compose.yml` with `docker-compose.full.yml`

## Running the Server

### With Docker Compose

```bash
# Build and start all services
docker-compose up -d --build

# View logs
docker-compose logs -f agreement-backend
```

### Standalone (for development)

```bash
# Start PostgreSQL
docker run -d --name postgres-dev \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=agreement_db \
  -p 5432:5432 \
  postgres:15-alpine

# Build and run
cd agreement-server
mvn clean install
mvn spring-boot:run
```

## API Endpoints

### Access Points

- **API Base URL**: http://localhost:8081/agreements/v3/api-docs
- **Swagger UI**: http://localhost:8081/agreements/swagger-ui/index.html
- **Health Check**: http://localhost:8081/agreements/actuator/health

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/login` | Login and get JWT token |

### Users

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/v1/users` | Get all users | Authenticated |
| GET | `/api/v1/users/{id}` | Get user by ID | Authenticated |
| GET | `/api/v1/users/me` | Get current user | Authenticated |
| POST | `/api/v1/users` | Create user | Master/Admin/Group Admin |
| PUT | `/api/v1/users/{id}` | Update user | Master/Admin/Group Admin |
| DELETE | `/api/v1/users/{id}` | Delete user | Master/Admin |

### Requestor Groups

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/v1/requestor-groups` | Get all groups | Authenticated |
| GET | `/api/v1/requestor-groups/{id}` | Get group by ID | Authenticated |
| POST | `/api/v1/requestor-groups` | Create group | Master/Admin |
| PUT | `/api/v1/requestor-groups/{id}` | Update group | Master/Admin |
| DELETE | `/api/v1/requestor-groups/{id}` | Delete group | Master/Admin |

### Agreements

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/v1/agreements` | Get all agreements | Authenticated |
| GET | `/api/v1/agreements/{id}` | Get agreement by ID | Authenticated |
| GET | `/api/v1/agreements/search?name=` | Search by name | Authenticated |
| POST | `/api/v1/agreements` | Create agreement | Master/Admin/Group Admin |
| PUT | `/api/v1/agreements/{id}` | Update agreement | Master/Admin/Group Admin |
| DELETE | `/api/v1/agreements/{id}` | Delete agreement | Master/Admin/Group Admin |

## Default Master Account

On first startup, a master account is created:
- **Email:** `master@agreements.com` (configurable via `REQUESTOR_MANAGER_MASTER_EMAIL`)
- **Password:** `Master@123` (configurable via `REQUESTOR_MANAGER_MASTER_PASSWORD`)

**⚠️ Important:** Change these credentials in production!

## Usage Examples

### Login
```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "master@agreements.com", "password": "Master@123"}'
```

### Create Requestor Group
```bash
curl -X POST http://localhost:8081/api/v1/requestor-groups \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{"name": "Engineering Team"}'
```

### Create User
```bash
curl -X POST http://localhost:8081/api/v1/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "password": "SecurePass123",
    "type": "REQUESTOR_GROUP_USER",
    "requestorGroupId": 1
  }'
```

### Create Agreement
```bash
curl -X POST http://localhost:8081/api/v1/agreements \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{
    "name": "Service Level Agreement",
    "description": "SLA for cloud services",
    "requestorGroupId": 1
  }'
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_USERNAME` | postgres | Database username |
| `DB_PASSWORD` | postgres | Database password |
| `REQUESTOR_MANAGER_DB_NAME` | agreement_db | Database name |
| `SERVER_PORT` | 8081 | Server port |
| `JWT_SECRET` | (default) | JWT signing secret |
| `JWT_EXPIRATION` | 86400000 | Token expiration (24h) |
| `MASTER_EMAIL` | master@agreements.com | Master account email |
| `MASTER_PASSWORD` | Master@123 | Master account password |
| `LOG_LEVEL` | INFO | Logging level |

## Security Notes

- All passwords are encrypted using BCrypt
- JWT tokens expire after 24 hours (configurable)
- Role-based access control on all endpoints
- CORS enabled for all origins (configure for production)
- Independent authentication from Keycloak

## License

GNU Affero General Public License, version 3 (AGPL-3.0-only), with additional terms under
Section 7 — see [`NOTICE`](../NOTICE) and [`License`](../License) at the repository root.