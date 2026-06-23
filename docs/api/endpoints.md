# RAGRO API — Endpoint Reference

Base URL: `http://localhost:8080`

All authenticated endpoints require the header: `Authorization: Bearer <token>`

> **Terminology**: The backlog uses "customer" and "producer" (user-facing terms). The database and code use "customer" and "farmer" respectively. See [conventions.md](../conventions.md#1-terminology-glossary) for the full mapping.

---

## Endpoints

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

#### POST /auth/register/customer

Registers a new customer (public). No auth required.

---

#### POST /auth/password/forgot

Starts the password-reset flow for a customer (public). No auth required.

---

#### POST /auth/password/reset

Resets the authenticated user's password. Requires valid JWT.

---

### Producer Dashboard

#### GET /producers/me/dashboard

Returns the authenticated producer's financial dashboard for a selected month. Requires `ROLE_FARMER`.

**Query Parameters:**
- `month` (optional, 1-12): Month for the dashboard. Defaults to current month.
- `year` (optional): Year for the dashboard. Defaults to current year.

**Response (200 OK):**
```json
{
  "month": 3,
  "year": 2026,
  "totalSales": "42850.00",
  "salesMetric": {
    "currentValue": "42850.00",
    "previousValue": "36250.50",
    "percentageChange": "+18.15"
  },
  "deliveredOrdersCount": 128,
  "ordersMetric": {
    "currentValue": "128",
    "previousValue": "114",
    "percentageChange": "+12.28"
  },
  "stockSoldPercentage": "85.00",
  "stockMetric": {
    "currentValue": "85.00",
    "previousValue": "86.75",
    "percentageChange": "-2.01"
  }
}
```

**Errors:**
- `401 Unauthorized` — token missing or invalid
- `403 Forbidden` — not a producer

---

#### GET /producers/me/dashboard/week

Returns the authenticated producer's weekly sales data for the last 7 days (including today). Requires `ROLE_FARMER`.

**Response (200 OK):**
```json
{
  "weekStartDate": "2026-03-01",
  "weekEndDate": "2026-03-07",
  "dailySales": [
    {
      "dayOfWeek": "Sunday",
      "date": "01/03",
      "fullDate": "2026-03-01",
      "orderCount": 12,
      "salesAmount": "1250.50"
    },
    {
      "dayOfWeek": "Monday",
      "date": "02/03",
      "fullDate": "2026-03-02",
      "orderCount": 18,
      "salesAmount": "1850.75"
    },
    {
      "dayOfWeek": "Tuesday",
      "date": "03/03",
      "fullDate": "2026-03-03",
      "orderCount": 15,
      "salesAmount": "1650.00"
    },
    {
      "dayOfWeek": "Wednesday",
      "date": "04/03",
      "fullDate": "2026-03-04",
      "orderCount": 22,
      "salesAmount": "2150.25"
    },
    {
      "dayOfWeek": "Thursday",
      "date": "05/03",
      "fullDate": "2026-03-05",
      "orderCount": 20,
      "salesAmount": "2050.00"
    },
    {
      "dayOfWeek": "Friday",
      "date": "06/03",
      "fullDate": "2026-03-06",
      "orderCount": 25,
      "salesAmount": "2450.75"
    },
    {
      "dayOfWeek": "Saturday",
      "date": "07/03",
      "fullDate": "2026-03-07",
      "orderCount": 16,
      "salesAmount": "1448.50"
    }
  ]
}
```

**Errors:**
- `401 Unauthorized` — token missing or invalid
- `403 Forbidden` — not a producer

---

## Endpoint Reference by Domain

The tables below list the routes exposed by each controller on the `develop` branch. See [backlog_ragro.md](../backlog_ragro.md) for the original product specifications.

### Authentication (`/auth`)

| Method | Route | Description |
|--------|-------|-------------|
| GET | /auth/config | Keycloak configuration (public) |
| GET | /auth/session | Authenticated user session |
| POST | /auth/register/customer | Customer registration (public) |
| POST | /auth/password/forgot | Start password reset (public) |
| POST | /auth/password/reset | Reset authenticated user's password |

### Customers (`/customers`)

| Method | Route | Description |
|--------|-------|-------------|
| GET | /customers/me | Retrieve authenticated customer profile |
| PUT | /customers/me | Update authenticated customer profile |

### Cart (`/customers/carts`)

| Method | Route | Description |
|--------|-------|-------------|
| GET | /customers/carts | Retrieve active cart |
| POST | /customers/carts/items | Add item to cart |
| PATCH | /customers/carts/items/{id} | Update item quantity |
| DELETE | /customers/carts/items/{id} | Remove item from cart |
| DELETE | /customers/carts | Clear cart |

### Favorite Producers (`/customers/me/favorites`)

| Method | Route | Description |
|--------|-------|-------------|
| GET | /customers/me/favorites | List favorited producers |
| POST | /customers/me/favorites/{producerId} | Favorite a producer |
| DELETE | /customers/me/favorites/{producerId} | Unfavorite a producer |

### Producers (`/producers`)

| Method | Route | Description |
|--------|-------|-------------|
| GET | /producers | List active producers (marketplace) |
| GET | /producers/locations | List producer map locations |
| GET | /producers/{id} | Retrieve complete producer profile |
| GET | /producers/{id}/profile | Retrieve public producer profile for customers |
| GET | /producers/{id}/products | List active products from a producer for customers |
| GET | /producers/{producerId}/products/{productId} | Retrieve a single producer product for customers |
| GET | /producers/{id}/reviews | List producer reviews |
| GET | /producers/me/dashboard | Get authenticated producer's financial dashboard |
| GET | /producers/me/dashboard/week | Get authenticated producer's weekly sales dashboard |
| PUT | /producers/{id} | Update producer profile |
| POST | /producers/{id}/avatar | Upload producer avatar image (multipart) |
| POST | /producers/{id}/cover | Upload producer cover image (multipart) |

### Products (`/producers/products`)

| Method | Route | Description |
|--------|-------|-------------|
| GET | /producers/products | List the authenticated producer's products |
| GET | /producers/products/{id} | Retrieve product |
| POST | /producers/products | Create product |
| PUT | /producers/products/{id} | Edit product |
| DELETE | /producers/products/{id} | Delete product (soft delete) |
| GET | /producers/products/categories | List product categories |
| POST | /producers/products/{id}/photo | Upload product photo (multipart) |

### Stock (`/producers/stock`)

| Method | Route | Description |
|--------|-------|-------------|
| GET | /producers/stock/movements | Stock movement history |
| GET | /producers/stock/{productId}/movements | Product movement history |
| POST | /producers/stock/entry | Register stock entry |
| POST | /producers/stock/exit | Register stock exit |

### Orders (`/orders`)

| Method | Route | Description |
|--------|-------|-------------|
| POST | /orders | Create order from cart |
| GET | /orders/consumer | Customer order history |
| GET | /orders/producer | Orders received by producer |
| GET | /orders/customer/{id} | Retrieve a single order |
| PATCH | /orders/{id}/status | Update delivery status |
| PATCH | /orders/{id}/confirm | Confirm order (producer) |
| PATCH | /orders/{id}/cancel | Cancel order (producer) |
| PATCH | /orders/customer/{id}/cancel | Cancel order (customer) |
| PATCH | /orders/customer/{id}/confirm-delivery | Confirm delivery (customer) |
| PATCH | /orders/{id}/confirm-delivery-with-code | Confirm delivery with 4-digit code |
| PATCH | /orders/{id}/refuse | Refuse order (producer) |
| PATCH | /orders/{id}/seen | Mark order as seen by producer |
| POST | /orders/{id}/repeat | Repeat previous order |

### Order Tracking (`/orders`)

| Method | Route | Description |
|--------|-------|-------------|
| GET | /orders/{id}/tracking | Retrieve real-time delivery tracking for an order |

### Reviews (`/reviews`)

| Method | Route | Description |
|--------|-------|-------------|
| POST | /reviews | Create review for delivered order |

### Delivery Routes (`/routes`)

| Method | Route | Description |
|--------|-------|-------------|
| POST | /routes | Optimize and create a delivery route (Google Routes API) |
| GET | /routes/active | Retrieve the active route |
| PATCH | /routes/{routeId}/stops/{stopId} | Update a route stop |

Real-time GPS ingestion uses STOMP: the producer sends `SEND /app/routes/{routeId}/position` and each accepted ping is rebroadcast on `/topic/routes/{routeId}` to authorized subscribers (`TrackingWsController`, enabled via `ragro.tracking.enabled`).

### CO2 Savings (`/co2`)

| Method | Route | Description |
|--------|-------|-------------|
| POST | /co2/calculate | Calculate CO2 savings for a scenario |
| GET | /co2/preference | Retrieve the customer's vehicle preference |
| GET | /co2/emissions | Retrieve emission factors |
| POST | /co2/record-savings | Record realized CO2 savings |
| GET | /co2/options | List vehicle options |
| GET | /co2/total-saved | Retrieve total CO2 saved |

### Recommendations (`/recommendations`)

| Method | Route | Description |
|--------|-------|-------------|
| GET | /recommendations | Product suggestions for customer (Spring AI → NVIDIA) |

### Search (`/search`)

| Method | Route | Description |
|--------|-------|-------------|
| GET | /search | Search marketplace products and producers |

### Notifications

| Method | Route | Description |
|--------|-------|-------------|
| POST | /notifications/token | Register FCM device token |
| GET | /customers/me/notifications | List the customer's notifications |
| GET | /customers/me/notifications/unread-count | Customer unread notification count |
| PATCH | /customers/me/notifications/{notificationId}/read | Mark a customer notification as read |
| PATCH | /customers/me/notifications/read-all | Mark all customer notifications as read |
| GET | /producers/me/notifications | List the producer's notifications |
| GET | /producers/me/notifications/unread-count | Producer unread notification count |
| PATCH | /producers/me/notifications/{notificationId}/read | Mark a producer notification as read |
| PATCH | /producers/me/notifications/read-all | Mark all producer notifications as read |

### Administration (`/admin`)

| Method | Route | Description |
|--------|-------|-------------|
| POST | /admin/producers | Register producer |
| GET | /admin/producers | List producers |
| GET | /admin/producers/{id} | Producer details |
| PUT | /admin/producers/{id} | Update producer |
| PATCH | /admin/producers/{id}/activate | Reactivate producer |
| PATCH | /admin/producers/{id}/deactivate | Deactivate producer |
| GET | /admin/customers/{id} | Customer details |
| GET | /admin/dashboard | Admin dashboard (returns JWT claims; debug stub) |

### Media (`/media`)

| Method | Route | Description |
|--------|-------|-------------|
| GET | /media/** | Serve MinIO-backed media (images, uploads) |
