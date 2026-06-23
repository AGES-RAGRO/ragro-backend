# RAGRO API — Overview

## Base URL

```
http://localhost:8080
```

In production, all requests are made over HTTPS. There is no API version in the URL.

---

## Authentication

The API uses **Bearer Token** (JWT) authentication via **Keycloak**.

The client obtains a JWT token from Keycloak using Direct Access Grants (Resource Owner Password):

```bash
curl -s -X POST http://localhost:8180/realms/ragro/protocol/openid-connect/token \
  -d "client_id=ragro-app" \
  -d "grant_type=password" \
  -d "username=customer@ragro.com.br" \
  -d "password=Test@123"
```

This token must be included in the `Authorization` header of all subsequent requests:

```
Authorization: Bearer <token>
```

The JWT token contains the following claims used by the backend:

| Claim | Description |
|-------|-------------|
| `sub` | Keycloak subject identifier — maps to `users.auth_sub` |
| `email` | User's email address |
| `groups` | User groups: `ADMIN`, `FARMER`, `CUSTOMER` |

**Token validation**: The backend validates the JWT signature using the Keycloak JWKS endpoint configured in `application.yml`. Expired or invalid tokens return `401 Unauthorized`.

---

## Role-Based Access Control

Endpoints are protected based on Keycloak group membership:

| URL Pattern | Required Role | Description |
|-------------|---------------|-------------|
| `/admin/**` | `ROLE_ADMIN` | Administrative operations |
| `/producers/**` | `ROLE_FARMER` | Farmer-specific operations |
| `/customers/**` | `ROLE_CUSTOMER` | Customer-specific operations |
| All other endpoints | Authenticated | Any valid JWT |

Roles are extracted from the `groups` claim and mapped to Spring Security authorities with the `ROLE_` prefix (e.g., `ADMIN` → `ROLE_ADMIN`).

### Public endpoints

A dedicated higher-priority filter chain (`SecurityConfig`, `@Order(1)`) permits unauthenticated access to the following routes, and ignores any token that is presented (so a stale token never `401`s a public route):

- `POST /auth/register/customer`
- `POST /auth/password/forgot`
- `GET /auth/config`
- `/media/**`
- `/actuator/health`
- `/co2/options`, `/co2/total-saved`, `/co2/calculate`
- Swagger UI / `/v3/api-docs` documentation routes

All other endpoints require a valid JWT and are governed by the RBAC rules above.

---

## Error Format

When a request fails, the API returns a standardized JSON error response:

```json
{
  "timestamp": "2026-03-30T12:00:00",
  "status": 400,
  "error": "Email already registered",
  "path": "/auth/register/customer"
}
```

Common HTTP error codes:

| Code | Exception | Meaning |
|------|-----------|---------|
| `400` | `BusinessException`, validation (`MethodArgumentNotValidException`/`BindException`), `MaxUploadSizeExceededException` | Business rule violation (e.g., duplicate email), invalid request body, or file too large |
| `401` | `UnauthorizedException` | Token missing, expired, or user not found |
| `403` | `ForbiddenException`, `AccessDeniedException` | Authenticated but not allowed (`AccessDeniedException` returns `"Acesso negado"`) |
| `404` | `NotFoundException` | Resource does not exist |
| `409` | `ConflictException`, optimistic-lock / data-integrity conflict | Concurrent or conflicting update |
| `422` | `GoogleApiException` (`INVALID_INPUT`) | Invalid input rejected by a Google integration (Geocoding/Routes) |
| `503` | `GoogleApiException` (`QUOTA`/`TRANSIENT`) | Google integration unavailable; includes a `Retry-After` header |
| `500` | `InternalServerException`, catch-all | Unexpected server error |

---

## Pagination

List endpoints support pagination via query parameters:

```
GET /producers?page=0&size=10
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | `int` | `0` | Page number (0-indexed) |
| `size` | `int` | per-endpoint | Items per page (defaults are per-endpoint `@RequestParam` values, not global — e.g. `GET /producers` defaults to `10`) |

Paginated list endpoints return a `PaginatedResponse` envelope rather than Spring's default `Page` JSON:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0
}
```

---

## CORS

CORS is configured in `CorsConfig` and does **not** use wildcards:

- **Allowed Origin Patterns**: defaults to `http://localhost:*`, configurable via the `cors.allowed-origin-patterns` property (comma-separated; production must include the API Gateway and frontend URLs)
- **Allowed Methods**: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `PATCH`
- **Allowed Headers**: `Content-Type`, `Authorization`
- **Exposed Headers**: `Authorization`
- **Allow Credentials**: `false` (stateless bearer-token API, no cookies)
- **Max Age**: `3600` seconds
