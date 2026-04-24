# RAGRO API — Endpoint Reference

Base URL: `http://localhost:8080`

All authenticated endpoints require the header: `Authorization: Bearer <token>`

> **Terminology**: The backlog uses "customer" and "producer" (user-facing terms). The database and code use "customer" and "farmer" respectively. See [conventions.md](../conventions.md#1-terminology-glossary) for the full mapping.

---

## Implemented Endpoints

### Authentication

#### GET /auth/config

Returns the Keycloak authentication configuration. No auth required.

**Response (200 OK):**
```json
{
  "tokenUrl": "http://localhost:8180/realms/ragro/protocol/openid-connect/token",
  "clientId": "ragro-app",
  "realm": "ragro"
}
```

---

#### GET /auth/session

Returns the authenticated user's session data. Requires valid JWT.

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Ricardo Aguiar",
  "email": "consumer@ragro.com.br",
  "type": "customer",
  "active": true
}
```

**Errors:**
- `401 Unauthorized` — token missing or user not found in database

---

### Admin — Users

#### POST /admin/users

Creates a new user. Requires `ROLE_ADMIN`.

**Request Body:**
```json
{
  "name": "João da Silva",
  "email": "joao@email.com",
  "phone": "(51) 98765-4321"
}
```

**Response (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "João da Silva",
  "email": "joao@email.com",
  "phone": "(51) 98765-4321",
  "type": null,
  "active": true,
  "createdAt": "2026-01-15T10:30:00-03:00",
  "updatedAt": "2026-01-15T10:30:00-03:00"
}
```

**Errors:**
- `400 Bad Request` — email already registered or authSub already exists

---

### Role-Based Access Verification (Debug / Test Only)

> **These endpoints are temporary** — they exist only to verify that role-based access control is working correctly. They return raw JWT claims and are **not part of the product backlog**. They will be replaced by the real domain endpoints as features are implemented.

#### GET /admin/dashboard

Verifies `ROLE_ADMIN` access. Returns JWT claims.

**Response (200 OK):**
```json
{
  "area": "admin",
  "sub": "keycloak-sub-uuid",
  "email": "admin@ragro.com.br",
  "groups": ["ADMIN"]
}
```

---

#### GET /producers/dashboard

Verifies `ROLE_FARMER` access. Returns JWT claims.

**Response (200 OK):**
```json
{
  "area": "farmer",
  "sub": "keycloak-sub-uuid",
  "email": "farmer@ragro.com.br",
  "groups": ["FARMER"]
}
```

---

#### GET /customers/orders

Verifies `ROLE_CUSTOMER` access. Returns JWT claims.

> **Warning**: This is NOT the real customer order history endpoint (`GET /orders/customer` from the backlog). This is a temporary test endpoint that will be replaced.

**Response (200 OK):**
```json
{
  "area": "customer",
  "sub": "keycloak-sub-uuid",
  "email": "customer@ragro.com.br",
  "groups": ["CUSTOMER"]
}
```

---

## Planned Endpoints

The following endpoints are defined in the product backlog and will be implemented as the project progresses. See [backlog_ragro.md](../backlog_ragro.md) for full specifications.

### Authentication

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| POST | /auth/register/customer | Customer registration | 1 |
| GET | /auth/config | Keycloak configuration (public) | 1 |
| GET | /auth/session | Authenticated user session | 1 |

### Customers

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| GET | /customers/me | Retrieve authenticated customer profile | 1 |
| PUT | /customers/me | Update authenticated customer profile | 1 |

### Producers

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| GET | /producers | List active producers (marketplace) | 2 |
| GET | /producers/:id | Retrieve complete producer profile (farmer owner only) | 3 |
| GET | /producers/:id/profile | Retrieve public producer profile for customers | 3 |
| GET | /producers/:id/products | List active products from a producer for customers | 3 |
| PUT | /producers/:id | Update producer profile | 1 |
| GET | /producers/:id/reviews | List producer reviews | 8 |

### Administration

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| POST | /admin/producers | Register producer | 1 |
| GET | /admin/producers | List producers (admin) | 1 |
| GET | /admin/producers/:id | Producer details (admin) | 1 |
| GET | /admin/customers/:id | Customer details (admin) | 1 |
| PATCH | /admin/producers/:id/deactivate | Deactivate producer | 1 |
| PATCH | /admin/producers/:id/activate | Reactivate producer | 1 |

### Products

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| POST | /products | Create product | 4 |
| GET | /products/:id | Retrieve product | 4 |
| PUT | /products/:id | Edit product | 4 |
| DELETE | /products/:id | Delete product (soft delete) | 4 |

### Stock

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| POST | /stock/entry | Register stock entry | 5 |
| POST | /stock/exit | Register stock exit | 5 |
| GET | /stock/:productId/movements | Product movement history | 5 |

### Cart

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| GET | /cart | Retrieve active cart | 6 |
| POST | /cart/items | Add item to cart | 6 |
| PUT | /cart/items/:id | Update item quantity | 6 |
| DELETE | /cart/items/:id | Remove item from cart | 6 |

### Orders

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| POST | /orders | Create order from cart | 7 |
| GET | /orders/customer | Customer order history | 7 |
| GET | /orders/producer | Orders received by producer | 7 |
| GET | /orders/today | Today's orders (producer) | 9 |
| PATCH | /orders/:id/confirm | Confirm order (producer) | 7 |
| PATCH | /orders/:id/cancel | Cancel order (customer) | 7 |
| PATCH | /orders/:id/status | Update delivery status | 7 |
| POST | /orders/:id/repeat | Repeat previous order | 7 |
| POST | /orders/:id/schedule | Schedule order for future date | 7 |

### Reviews

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| POST | /reviews | Create review for delivered order | 8 |

### Logistics

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| GET | /logistics/route | Generate optimized daily route | 9 |

### Recommendations

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| GET | /recommendations | Product suggestions for customer | 10 |

### Financial Dashboard

| Method | Route | Description | Epic |
|--------|-------|-------------|------|
| GET | /dashboard/producer/summary | Producer financial summary | 11 |
| GET | /dashboard/producer/sales | Producer sales history | 11 |
