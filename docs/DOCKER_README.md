# Ragro Backend - Docker Setup

## Requirements

- Docker 20.10+
- Docker Compose 2.0+

## Quick Start

Bring up the application with a single command:

```bash
docker compose up --build
```

This will:
1. **Build** the backend Docker image
2. **Start** PostgreSQL with the schema applied automatically
3. **Create** the `keycloak` database inside PostgreSQL (via `data/00-create-keycloak-db.sh`)
4. **Start** Keycloak 26 with the pre-configured `ragro` realm
5. **Start** MinIO (object storage for media) and Mailpit (SMTP testing)
6. **Wait** for PostgreSQL and MinIO health checks to pass
7. **Start** the backend connected to PostgreSQL, Keycloak, and MinIO

> **Prerequisites:** copy `.env.example` to `.env` and fill in the required keys before bringing the stack up. `GOOGLE_MAPS_API_KEY` (geocoding + routes) has no default and must be set. `NVIDIA_API_KEY` (LLM reranker) and `FIREBASE_SERVICE_ACCOUNT_JSON` (FCM push) are optional — leave them empty to disable those features.

## Application Access

- **Backend**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health
- **Keycloak Admin Console**: http://localhost:8180
  - User: `admin`
  - Password: `admin`
- **PostgreSQL**: localhost:5432
  - User: `postgres`
  - Password: `postgres`
  - Databases: `gearheads` (application), `keycloak` (Keycloak)
- **MinIO Console**: http://localhost:9001 (API at http://localhost:9000)
  - User: `minioadmin`
  - Password: `minioadmin`
  - Bucket: `ragro-media`
- **Mailpit (email testing)**: http://localhost:8025 (SMTP at localhost:1025)
  - Catches password-reset / verification emails sent via Keycloak

> All published ports are bound to the loopback interface (`127.0.0.1`), so they are reachable only from the host machine.

## Docker Structure

### Dockerfile

- **Base image**: `eclipse-temurin:21-jre-alpine` (optimized Java 21)
- **Build**: multi-stage with Maven 3.9 to reduce final size
- **Health Check**: automatic validation every 30s via `/actuator/health`

### docker-compose.yml

#### Service: `postgres`
- Image: `postgres:16-alpine`
- Volumes:
  - Data persisted in `postgres_data:/var/lib/postgresql/data`
  - Init script: `data/00-create-keycloak-db.sh` (creates the `keycloak` database)
- Health Check: validates the database connection

#### Service: `keycloak`
- Image: `quay.io/keycloak/keycloak:26.0`
- Mode: `start-dev` with `--import-realm`
- Database: shared PostgreSQL (`keycloak` database)
- Port: `8180`
- Realm: imported from `keycloak/ragro-realm.json`, containing:
  - `ragro-app` client (public, Direct Access Grants enabled)
  - Groups: `ADMIN`, `CUSTOMER`, `FARMER`
  - Mapper: `groups` claim in the JWT
  - Pre-configured test users
  - Custom `ragro` login theme (mounted from `./keycloak/themes`) with pt-BR localization
  - `smtpServer` pointing at the `mailpit` container (host `mailpit:1025`) for outgoing email
- Depends on: PostgreSQL (waits for health check)

#### Service: `minio`
- Image: `quay.io/minio/minio:latest`
- Object storage for media; the backend reads/writes here and serves files via `GET /media/**`
- Root credentials: `minioadmin` / `minioadmin`, bucket `ragro-media`
- Ports: `9000` (API), `9001` (console)
- Health Check: `GET /minio/health/live`

#### Service: `mailpit`
- Image: `axllent/mailpit`
- SMTP server + web UI for capturing outgoing email in development
- Ports: `1025` (SMTP), `8025` (web UI)
- Keycloak's realm `smtpServer` is wired to this container, so password-reset and verification emails land in the Mailpit inbox

#### Service: `backend`
- Built from `Dockerfile`
- Migrations run automatically via Flyway (`src/main/resources/db/migration`)
- Key environment variables (see `.env.example` for the full template):
  - `SPRING_DATASOURCE_URL`: jdbc:postgresql://postgres:5432/gearheads
  - `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`: postgres / postgres
  - `SPRING_JPA_HIBERNATE_DDL_AUTO`: validate (schema is owned by Flyway)
  - `KEYCLOAK_SERVER_URL`: http://keycloak:8180 (internal communication)
  - `KEYCLOAK_PUBLIC_URL`: http://localhost:8180 (Swagger UI / browser)
  - `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`: admin / admin
  - `KEYCLOAK_ISSUER_URI`: http://localhost:8180/realms/ragro (must match the token issuer, i.e. the public URL)
  - `KEYCLOAK_JWK_SET_URI`: http://keycloak:8180/realms/ragro/protocol/openid-connect/certs (internal hostname)
  - `STORAGE_ENDPOINT`: http://minio:9000 (internal); `STORAGE_PUBLIC_URL`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_BUCKET`
  - `MEDIA_PUBLIC_URL`: base URL the backend uses for `GET /media/**` links
  - `NVIDIA_API_KEY`: LLM reranker (optional; empty = disabled)
  - `GOOGLE_MAPS_API_KEY`: geocoding + Google Routes (required, no default)
  - `FIREBASE_SERVICE_ACCOUNT_JSON`: FCM push credentials (optional; empty = push disabled)
- Depends on: PostgreSQL and MinIO (waits for both health checks)
- Network: isolated in `ragro-network`

## Docker Files

```
/Dockerfile                       - Backend Docker image (multi-stage)
/docker-compose.yml               - Orchestration: postgres + keycloak + minio + mailpit + backend
/docker-compose.test.yml          - Orchestration for tests (postgres + backend only, fully env-driven)
/.env.example                     - Template of required env vars (copy to .env)
/.dockerignore                    - Files ignored in the build
/src/main/resources/db/migration/ - Flyway migrations (schema and seed)
/data/00-create-keycloak-db.sh    - Init script: creates keycloak database in postgres
/keycloak/ragro-realm.json        - Pre-configured Keycloak realm
/keycloak/themes/                 - Custom Keycloak login theme (ragro)
```

### docker-compose.test.yml

A trimmed compose used by CI / integration tests: only `postgres` and `backend`, no Keycloak/MinIO/Mailpit. Every value is interpolated from environment variables (`${POSTGRES_*}`, `${SPRING_*}`), typically supplied via an `.env.test` file or the CI environment.

## Configuration in application.yml

Environment variables are defined in `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/gearheads}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:postgres}
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/ragro}
          jwk-set-uri: ${KEYCLOAK_JWK_SET_URI:http://localhost:8180/realms/ragro/protocol/openid-connect/certs}

keycloak:
  server-url: ${KEYCLOAK_SERVER_URL:http://localhost:8180}
  public-url: ${KEYCLOAK_PUBLIC_URL:http://localhost:8180}
  realm: ragro
  admin:
    username: ${KEYCLOAK_ADMIN:admin}
    password: ${KEYCLOAK_ADMIN_PASSWORD:admin}
```

- **In Docker**: uses values from `docker-compose.yml`
- **Local**: uses defaults (localhost)

## Useful Commands

### Start everything
```bash
docker compose up --build
```

### Start in background
```bash
docker compose up --build -d
```

### View logs
```bash
docker compose logs -f backend
docker compose logs -f keycloak
docker compose logs -f postgres
```

### Stop everything
```bash
docker compose down
```

### Remove volumes (clear data and recreate realm)
```bash
docker compose down -v
```

### Rebuild images
```bash
docker compose build --no-cache
```

### Start infrastructure only (for local dev)
```bash
docker compose up postgres keycloak minio mailpit -d
```

## Troubleshooting

### Port 5432, 8080, 8180, 9000/9001, or 1025/8025 already in use

Edit `docker-compose.yml` to use different ports (keep the `127.0.0.1:` loopback prefix used throughout):

```yaml
ports:
  - "127.0.0.1:5433:5432"  # PostgreSQL
  - "127.0.0.1:8081:8080"  # Backend
  - "127.0.0.1:8181:8180"  # Keycloak
```

### Backend can't connect to PostgreSQL

Check that the postgres service is healthy:

```bash
docker compose ps
```

The backend waits for the postgres health check (up to 5 retries of 10s each = ~50s).

### Keycloak doesn't import the realm

If the realm doesn't appear in the Keycloak admin console:

1. Verify `keycloak/ragro-realm.json` exists
2. Recreate the volumes: `docker compose down -v && docker compose up --build`
3. The realm is imported only on first startup

### Swagger UI returns "Failed to fetch" on auth

Verify `KEYCLOAK_PUBLIC_URL` is `http://localhost:8180` (not the internal hostname `keycloak`).

### Reinitialize with a new schema

```bash
docker compose down -v
docker compose up --build
```

This removes all volumes, forces a rebuild, reapplies the schema, and reimports the realm.

## Performance

- **Multi-stage build**: reduces final image size (~200MB vs ~500MB)
- **Alpine Linux**: minimal Java 21 image
- **Shared PostgreSQL**: Keycloak and the application share one PostgreSQL container with separate databases
- **Network isolation**: services isolated in a custom network
- **Health checks**: ensure readiness before starting dependencies

## Environment Configuration (`.env`)

`docker-compose.yml` already interpolates several values from a local `.env` file (e.g. `STORAGE_PUBLIC_URL`, `MEDIA_PUBLIC_URL`, `NVIDIA_API_KEY`, `GOOGLE_MAPS_API_KEY`, `FIREBASE_SERVICE_ACCOUNT_JSON`). A committed `.env.example` documents every supported variable.

```bash
cp .env.example .env
# then fill in GOOGLE_MAPS_API_KEY (required) and, if needed, NVIDIA_API_KEY / FIREBASE_SERVICE_ACCOUNT_JSON
```

The `.env` file is gitignored — keep secrets out of version control.

## Production Security

For production, also override the default credentials via `.env` (do not rely on the `admin`/`postgres`/`minioadmin` defaults baked into the compose file):

```
POSTGRES_PASSWORD=senha_segura
SPRING_DATASOURCE_PASSWORD=senha_segura
KEYCLOAK_ADMIN_PASSWORD=senha_segura
```

Also:
- Set `sslRequired` to `external` in the Keycloak realm
- Restrict `webOrigins` on the `ragro-app` client to specific domains
- Disable Swagger UI in production
- Use Docker Secrets or an external secrets manager
