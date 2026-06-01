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
5. **Wait** for the database to be ready
6. **Start** the backend connected to the database and Keycloak

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
- Depends on: PostgreSQL (waits for health check)

#### Service: `backend`
- Built from `Dockerfile`
- Migrations run automatically via Flyway (`src/main/resources/db/migration`)
- Environment variables:
  - `SPRING_DATASOURCE_URL`: jdbc:postgresql://postgres:5432/gearheads
  - `KEYCLOAK_SERVER_URL`: http://keycloak:8180 (internal communication)
  - `KEYCLOAK_PUBLIC_URL`: http://localhost:8180 (Swagger UI / browser)
  - `KEYCLOAK_ISSUER_URI`: http://keycloak:8180/realms/ragro
  - `KEYCLOAK_JWK_SET_URI`: http://keycloak:8180/realms/ragro/protocol/openid-connect/certs
- Depends on: PostgreSQL (waits for health check)
- Network: isolated in `ragro-network`

## Docker Files

```
/Dockerfile                       - Backend Docker image (multi-stage)
/docker-compose.yml               - Orchestration: postgres + keycloak + backend
/docker-compose.test.yml          - Orchestration for tests
/.dockerignore                    - Files ignored in the build
/src/main/resources/db/migration/ - Flyway migrations (schema and seed)
/data/00-create-keycloak-db.sh    - Init script: creates keycloak database in postgres
/keycloak/ragro-realm.json        - Pre-configured Keycloak realm
```

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
docker compose up postgres keycloak -d
```

## Troubleshooting

### Port 5432, 8080, or 8180 already in use

Edit `docker-compose.yml` to use different ports:

```yaml
ports:
  - "5433:5432"  # PostgreSQL
  - "8081:8080"  # Backend
  - "8181:8180"  # Keycloak
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

## Production Security

For production, consider:

```yaml
# docker-compose.yml
environment:
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
  SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}
  KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
```

Use an `.env` file (not versioned):

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
