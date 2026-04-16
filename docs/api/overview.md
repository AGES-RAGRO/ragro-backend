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

---

## Error Format

When a request fails, the API returns a standardized JSON error response:

```json
{
  "timestamp": "2026-03-30T12:00:00",
  "status": 400,
  "error": "Email already registered",
  "path": "/users"
}
```

Common HTTP error codes:

| Code | Exception | Meaning |
|------|-----------|---------|
| `400` | `BusinessException` | Business rule violation (e.g., duplicate email) |
| `401` | `UnauthorizedException` | Token missing, expired, or user not found |
| `404` | `NotFoundException` | Resource does not exist |

---

## Pagination

List endpoints support pagination via query parameters:

```
GET /producers?page=0&size=20
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | `int` | `0` | Page number (0-indexed) |
| `size` | `int` | `20` | Items per page |

---

## CORS

CORS is configured to accept requests from all origins during development:

- **Allowed Origins**: `*`
- **Allowed Methods**: `*`
- **Allowed Headers**: `*`
