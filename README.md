# RAGRO Backend

REST API for the RAGRO platform — connecting urban customers with local family farmers.

**Stack:** Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Keycloak 26 · Docker

---

## Documentation

| Document | Description |
|----------|-------------|
| **API** | |
| [API Overview](docs/api/overview.md) | Base URL, authentication, error format, CORS, pagination |
| [Endpoint Reference](docs/api/endpoints.md) | All implemented and planned endpoints |
| **Architecture** | |
| [Overview](docs/architecture/01-overview.md) | Tech stack, architectural pattern, request flow |
| [Project Structure](docs/architecture/02-project-structure.md) | Annotated package and folder structure |
| [Layers](docs/architecture/03-layers.md) | Controller, Service, Repository, Domain responsibilities |
| [Security](docs/architecture/04-security.md) | JWT, Keycloak, role-based access control |
| [Error Handling](docs/architecture/05-error-handling.md) | Exception hierarchy and standardized error responses |
| **Standards** | |
| [Conventions](docs/conventions.md) | Naming, coding, database, and workflow conventions |
| **Reference** | |
| [Database](docs/database.md) | Full schema documentation — 21 tables, ER diagram, triggers |
| [Product Backlog](docs/backlog_ragro.md) | All epics, user stories, and acceptance criteria |

---

## Prerequisites

| Tool | Version |
|------|---------|
| Docker | 24+ |
| Docker Compose | v2+ |
| Java | 21 (local dev only) |
| Maven | 3.9+ (local dev only) |

---

## Quick Start (Docker)

The fastest way to run the full stack locally.

**1. Clone the repository**

```bash
git clone https://github.com/AGES-RAGRO/ragro-backend.git
cd ragro-backend
```

**2. Start the services**

```bash
docker compose up --build
```

This starts:
- `ragro-postgres` — PostgreSQL 16 on port `5432`, initialized with `data/schema.sql`
- `ragro-keycloak` — Keycloak 26 on port `8180`, pre-configured with realm `ragro`
- `ragro-backend` — Spring Boot API on port `8080`

**3. Verify the services are running**

```bash
# Backend health check
curl http://localhost:8080/actuator/health

# Keycloak admin console
open http://localhost:8180  # admin / admin
```

---

## Local Development (without Docker for the backend)

**1. Start the infrastructure (database + Keycloak)**

```bash
docker compose up postgres keycloak -d
```

**2. Run the application**

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`. No additional configuration is needed — the defaults in `application.yml` point to `localhost:8180` for Keycloak and `localhost:5432` for PostgreSQL.

---

## Authentication with Keycloak

All protected endpoints require a **Bearer JWT token** issued by Keycloak.

### How it works

1. The client sends credentials (email + password) to Keycloak's token endpoint
2. Keycloak validates and returns a JWT containing `sub`, `email`, and `groups`
3. The client includes the JWT in all API requests:
   ```
   Authorization: Bearer <token>
   ```
4. The backend validates the JWT signature and maps `groups` to Spring Security roles

### Pre-configured test users

| Email | Password | Role |
|-------|----------|------|
| `admin@ragro.com.br` | `Admin@123` | ADMIN |
| `customer@ragro.com.br` | `Test@123` | CUSTOMER |
| `farmer@ragro.com.br` | `Test@123` | FARMER |

### Obtaining a token (via curl)

```bash
curl -s -X POST http://localhost:8180/realms/ragro/protocol/openid-connect/token \
  -d "client_id=ragro-app" \
  -d "grant_type=password" \
  -d "username=customer@ragro.com.br" \
  -d "password=Test@123"
```

### Registering a new customer (public endpoint)

```bash
curl -X POST http://localhost:8080/auth/register/customer \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Maria Silva",
    "email": "maria@example.com",
    "password": "Senha@123",
    "phone": "(51) 99999-9999",
    "fiscalNumber": "123.456.789-01",
    "address": {
      "street": "Rua das Flores",
      "number": "100",
      "city": "Porto Alegre",
      "state": "RS",
      "zipCode": "90010-120"
    }
  }'
```

This creates the user in both Keycloak and the application database. The user can then log in immediately.

See [Security docs](docs/architecture/04-security.md) for the full authentication flow.

---

## API Documentation (Swagger UI)

The API is fully documented with **OpenAPI 3.0** and can be explored interactively via **Swagger UI**.

### Access Swagger UI

Once the application is running, open your browser and navigate to:

```
http://localhost:8080/swagger-ui.html
```

### Authenticating in Swagger UI

Swagger UI is integrated with Keycloak's OAuth2 password flow — no need to copy tokens manually:

1. Click the **Authorize** button (lock icon)
2. Fill in the form:
   - **client_id**: `ragro-app`
   - **username**: e.g., `customer@ragro.com.br`
   - **password**: e.g., `Test@123`
3. Click **Authorize**
4. All subsequent requests will include the JWT automatically

### Available Endpoints in Swagger

- **Authentication** — Customer registration (`/auth/register/customer`)
- **Users** — User profile operations (`/users/me`)
- **Customers** — Customer-specific operations (requires `ROLE_CUSTOMER`)
- **Admin** — Administrative endpoints (requires `ROLE_ADMIN`)
- **Farmer** — Farmer-specific operations (requires `ROLE_FARMER`)

### API Documentation Files

- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs` — Raw OpenAPI specification
- **Swagger UI**: `http://localhost:8080/swagger-ui.html` — Interactive documentation

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/gearheads` | Database JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Database password |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` | Hibernate DDL strategy |
| `KEYCLOAK_SERVER_URL` | `http://localhost:8180` | Keycloak server URL (internal, backend-to-Keycloak) |
| `KEYCLOAK_PUBLIC_URL` | `http://localhost:8180` | Keycloak public URL (browser-facing, used by Swagger UI) |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/ragro` | JWT issuer URI for token validation |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:8180/realms/ragro/protocol/openid-connect/certs` | JWKS endpoint for JWT signature verification |
| `KEYCLOAK_ADMIN` | `admin` | Keycloak admin username (used for user registration via Admin API) |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | Keycloak admin password |

---

## Running Tests

```bash
./mvnw test
```

To run integration tests with a fresh database:

```bash
docker compose -f docker-compose.test.yml up --abort-on-container-exit
```

---

## Project Structure

```
src/
  main/
    java/br/com/ragro/       # Application source code
      config/                 # Security, CORS, Keycloak converters, OpenAPI
      controller/             # REST endpoints and DTOs
      domain/                 # JPA entities and enums
      service/                # Business logic
      repository/             # Data access (Spring Data JPA)
      mapper/                 # Entity <-> DTO converters
      exception/              # Custom exceptions and global handler
    resources/
      application.yml         # Configuration
data/
  schema.sql                  # Database schema (auto-applied on first run)
  00-create-keycloak-db.sh    # Init script to create Keycloak database
keycloak/
  ragro-realm.json            # Pre-configured Keycloak realm (groups, client, test users)
docs/
  api/                        # API documentation
  architecture/               # Architecture documentation
  conventions.md              # Project conventions
  database.md                 # Database schema documentation
  backlog_ragro.md            # Product backlog
```
