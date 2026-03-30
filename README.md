# RAGRO Backend

REST API for the RAGRO platform — connecting urban consumers with local family farmers.

**Stack:** Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Docker · AWS Cognito (JWT)

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
- `ragro-backend` — Spring Boot API on port `8080`

**3. Verify the API is running**

```bash
curl http://localhost:8080/actuator/health
```

Expected response: `{"status":"UP"}`

---

## Local Development (without Docker)

**1. Start only the database**

```bash
docker compose up postgres -d
```

**2. Configure AWS Cognito**

Edit `src/main/resources/application.yml` and replace the placeholders:

```yaml
security:
  oauth2:
    resourceserver:
      jwt:
        issuer-uri: https://cognito-idp.<REGION>.amazonaws.com/<USER_POOL_ID>
```

**3. Run the application**

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/gearheads` | Database JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Database password |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` | Hibernate DDL strategy |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | — | Cognito JWKS endpoint (required in production) |

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
    java/br/com/ragro/   # Application source code
    resources/
      application.yml    # Configuration
      authz.pem          # Public key for JWT validation
data/
  schema.sql             # Database schema (auto-applied on first run)
```

---

## Authentication

All protected endpoints require a **Bearer JWT token** issued by AWS Cognito.

```
Authorization: Bearer <token>
```

Configure the Cognito User Pool region and ID in `application.yml` before running.
