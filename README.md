# RAGRO Backend

REST API for the RAGRO platform — connecting urban customers with local family farmers.

**Stack:** Java 21 · Spring Boot 3.5.14 · PostgreSQL 16 · Keycloak 26 · MinIO · Docker

Highlights: delivery-route optimization (Google Routes API), real-time GPS tracking (WebSocket/STOMP), CO2-savings tracking, LLM-powered recommendations (Spring AI → NVIDIA), and FCM push notifications.

---

# Community

![Alt](https://repobeats.axiom.co/api/embed/35cf2791321772402da64da98fb075a4a10c4910.svg "Repobeats analytics image")

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
| [Gitflow](docs/GITFLOW.md) | Branching model and pull-request workflow |
| **Operations** | |
| [Docker Setup](docs/DOCKER_README.md) | Running the full stack (backend, PostgreSQL, Keycloak, MinIO, Mailpit) with Docker Compose |
| **Reference** | |
| [Database](docs/database.md) | Full schema documentation — 26 tables, ER diagram, triggers |
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
- `ragro-postgres` — PostgreSQL 16 on port `5432`
- `ragro-keycloak` — Keycloak 26 on port `8180`, pre-configured with realm `ragro`
- `ragro-minio` — MinIO object storage on port `9000` (bucket `ragro-media`, serves uploaded media)
- `ragro-backend` — Spring Boot API on port `8080` (applies Flyway migrations automatically)

**3. Verify the services are running**

```bash
# Backend health check
curl http://localhost:8080/actuator/health

# Keycloak admin console
open http://localhost:8180  # admin / admin
```

---

## LLM Re-Ranker (NVIDIA LLM API)

A hosted AI model re-ranks product recommendations. The NVIDIA LLM API is OpenAI-compatible, so it
is consumed through Spring AI's OpenAI client (`spring.ai.openai.*`, base-url
`https://integrate.api.nvidia.com`). No local model or container is required.

### Setup

Get an API key at [build.nvidia.com](https://build.nvidia.com) and set it (the `.env` is
git-ignored):

```bash
# .env
NVIDIA_API_KEY=nvapi-...
NVIDIA_MODEL=meta/llama-3.1-8b-instruct   # optional; this is the default (instruct, non-reasoning)
```

> Use an **instruct** (non-reasoning) model. Reasoning models (e.g. `stepfun-ai/step-3.7-flash`)
> generate a long chain-of-thought per request and blow the token/time budget when re-ranking ~50
> candidates, so they always end up in the heuristic fallback. For higher quality at more latency,
> `meta/llama-3.3-70b-instruct` is a drop-in via `NVIDIA_MODEL`.

In AWS ECS the key is injected from the `NVIDIA_API_KEY` GitHub Actions secret (see
`.github/workflows/aws.yml`).

### Fallback

If the NVIDIA LLM API is unavailable or no key is set, recommendations fall back to the heuristic
algorithm with no loss of functionality.

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

The **Keycloak** realm (`keycloak/ragro-realm.json`) ships with the three accounts below. In the **database**, however, migration `V18__remove_seed_test_customers_farmers.sql` removes the seeded customer and farmer rows, so after all Flyway migrations run only the **admin** is seeded in `users`. To exercise the customer/farmer flows, register an account via the public registration endpoint (see below), which creates the matching `users` + `customers` row.

| Email | Password | Role | DB seed (after migrations) |
|-------|----------|------|----------------------------|
| `admin@ragro.com.br` | `Admin@123` | ADMIN | `users` (seeded) |
| `customer@ragro.com.br` | `Test@123` | CUSTOMER | Keycloak only — register to create the DB row |
| `farmer@ragro.com.br` | `Test@123` | FARMER | Keycloak only — register to create the DB row |

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

Other public (no-token) `/auth` endpoints handled by the public security chain are `GET /auth/config` (client config for the app) and `POST /auth/password/forgot` (request a password-reset email). The reset itself (`POST /auth/password/reset`) and `GET /auth/session` are authenticated.

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

The API exposes 20 controllers. The main groups:

- **Auth** — Registration, config, password forgot/reset, session (`/auth/**`)
- **Customers** — Customer-specific operations (requires `ROLE_CUSTOMER`)
- **Producers** — Producer-specific operations (requires `ROLE_FARMER`)
- **Admin** — Administrative endpoints (requires `ROLE_ADMIN`)
- **Products** / **Search** / **Stock** — Product catalog, search, and inventory
- **Cart** / **Orders** — Shopping cart and order placement
- **Order Tracking** / **Routes** — Delivery-route optimization and order tracking, plus real-time GPS via WebSocket/STOMP (`TrackingWsController`)
- **Co2** — CO2-savings calculation and tracking (`/co2`)
- **Recommendations** — LLM-powered product recommendations (`/recommendations`)
- **Reviews** / **Favorites** — Product reviews and favorite producers
- **Media** — Uploaded media served via `GET /media/**` (MinIO)
- **Notifications** — Push (FCM) and in-app notifications (customer/producer)

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
| `STORAGE_ENDPOINT` | `http://localhost:9000` | MinIO/S3 endpoint for object storage |
| `STORAGE_BUCKET` | `ragro-media` | Bucket name for uploaded media |
| `STORAGE_ACCESS_KEY` | — | MinIO/S3 access key |
| `STORAGE_SECRET_KEY` | — | MinIO/S3 secret key |
| `MINIO_PUBLIC_URL` | `http://localhost:9000` | Client-facing media base URL (use `10.0.2.2` on the Android emulator) |
| `GOOGLE_MAPS_API_KEY` | — | Server-side Google Maps key for route optimization (Routes API) and geocoding |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | — | Firebase service-account JSON (single line) for FCM push notifications |
| `NVIDIA_API_KEY` | — | NVIDIA LLM API key for the recommendation re-ranker (optional; falls back to heuristic) |
| `NVIDIA_MODEL` | `meta/llama-3.1-8b-instruct` | NVIDIA model used by the re-ranker (use an instruct, non-reasoning model) |

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
      controller/             # REST + WebSocket endpoints
        request/              #   request DTOs
        response/             #   response DTOs
      domain/                 # JPA entities (also enums/, llm/, event/, specification/)
      service/                # Business logic (api/ interfaces, impl/ implementations)
      repository/             # Data access (Spring Data JPA)
      mapper/                 # Entity <-> DTO converters
      exception/              # Custom exceptions and global handler
      validation/             # Custom Bean Validation constraints
      event/                  # Application events
      listener/               # Event listeners (e.g. post-commit FCM dispatch)
    resources/
      application.yml         # Configuration
data/
  00-create-keycloak-db.sh    # Init script to create Keycloak database
src/
  main/
    resources/
      db/
        migration/
          V1__initial_schema.sql      # Initial schema migration
          V2__seed_test_users.sql     # Seed users synced with Keycloak
          ...                         # 23 migrations total (V1–V24, V15 skipped)
          V24__route_positions.sql    # Latest migration (real-time GPS positions)
keycloak/
  ragro-realm.json            # Pre-configured Keycloak realm (groups, client, test users)
docs/
  api/                        # API documentation
  architecture/               # Architecture documentation
  conventions.md              # Project conventions
  database.md                 # Database schema documentation
  backlog_ragro.md            # Product backlog
```
