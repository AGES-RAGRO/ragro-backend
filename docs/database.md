# RAGRO — Database Documentation

> PostgreSQL 16 · 27 tables · 2 triggers

> **Note — Schema Reference**: This document serves as the **design reference** for the database schema. The actual runtime schema is defined by Flyway migrations in `src/main/resources/db/migration` (starting at `V1__initial_schema.sql`).

## Table of Contents

- [Entity-Relationship Diagram](#entity-relationship-diagram)
- [Domains](#domains)
- [Tables](#tables)
  - [Users and Profiles](#users-and-profiles)
  - [Products and Inventory](#products-and-inventory)
  - [Cart and Orders](#cart-and-orders)
  - [Reviews and Favorites](#reviews-and-favorites)
  - [Logistics](#logistics)
  - [Sustainability (CO2)](#sustainability-co2)
  - [Payment](#payment)
- [Triggers](#triggers)
---

## Entity-Relationship Diagram

```mermaid
erDiagram
    users {
        uuid id PK
        varchar name
        varchar email
        varchar phone
        varchar type
        boolean active
        text auth_sub
        timestamptz created_at
        timestamptz updated_at
    }
    farmers {
        uuid id PK_FK
        varchar fiscal_number
        varchar fiscal_number_type
        varchar farm_name
        text description
        text avatar_s3
        text display_photo_s3
        integer total_reviews
        decimal average_rating
        integer total_orders
        decimal total_sales_amount
        timestamptz created_at
        timestamptz updated_at
    }
    producer_profiles {
        uuid id PK_FK
        text story
        text photo_url
        date member_since
        timestamptz created_at
        timestamptz updated_at
    }
    customers {
        uuid id PK_FK
        char fiscal_number
        timestamptz created_at
        timestamptz updated_at
    }
    addresses {
        uuid id PK
        uuid user_id FK
        varchar street
        varchar number
        varchar neighborhood
        varchar city
        char state
        char zip_code
        decimal latitude
        decimal longitude
        varchar geocode_status
        timestamptz geocoded_at
        boolean is_primary
        timestamptz created_at
    }
    farmer_availability {
        uuid id PK
        uuid farmer_id FK
        smallint weekday
        time opens_at
        time closes_at
        boolean active
    }
    product_categories {
        serial id PK
        varchar name
        text description
    }
    products {
        uuid id PK
        uuid farmer_id FK
        varchar name
        text description
        decimal price
        varchar unity_type
        decimal stock_quantity
        text image_s3
        boolean active
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }
    product_category_assignments {
        uuid product_id PK_FK
        integer category_id PK_FK
    }
    product_photos {
        uuid id PK
        uuid product_id FK
        text url
        smallint display_order
        timestamptz created_at
    }
    stock_movements {
        uuid id PK
        uuid product_id FK
        varchar type
        varchar reason
        decimal quantity
        text notes
        timestamptz created_at
        timestamptz updated_at
    }
    carts {
        uuid id PK
        uuid customer_id FK
        uuid farmer_id FK
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }
    cart_items {
        uuid id PK
        uuid cart_id FK
        uuid product_id FK
        decimal quantity
        boolean active
    }
    orders {
        uuid id PK
        uuid customer_id FK
        uuid farmer_id FK
        uuid delivery_address_id FK
        jsonb delivery_address_snapshot
        varchar status
        uuid payment_method_id FK
        varchar payment_status
        timestamptz scheduled_for
        timestamptz delivered_at
        text notes
        text cancellation_reason
        text cancellation_details
        varchar confirmation_code
        integer confirmation_attempts
        timestamptz confirmation_locked_until
        boolean seen_by_farmer
        timestamptz created_at
        timestamptz updated_at
    }
    order_items {
        uuid id PK
        uuid order_id FK
        uuid product_id FK
        varchar product_name_snapshot
        decimal unit_price_snapshot
        varchar unity_type_snapshot
        decimal quantity
        decimal subtotal
    }
    order_status_history {
        uuid id PK
        uuid order_id FK
        varchar status
        timestamptz changed_at
    }
    review {
        uuid id PK
        uuid order_id FK
        uuid farmer_id FK
        uuid customer_id FK
        smallint rating
        text comment
        timestamptz created_at
    }
    favorites {
        uuid customer_id PK_FK
        uuid farmer_id PK_FK
        timestamptz created_at
    }
    delivery_routes {
        uuid id PK
        uuid farmer_id FK
        varchar status
        decimal origin_latitude
        decimal origin_longitude
        decimal total_distance_km
        integer total_duration_seconds
        decimal baseline_distance_km
        text overview_polyline
        timestamptz created_at
        timestamptz completed_at
    }
    route_stops {
        uuid id PK
        uuid route_id FK
        uuid order_id FK
        integer sequence
        varchar status
        decimal latitude
        decimal longitude
        text address_text
        decimal leg_distance_km
        integer leg_duration_seconds
        timestamptz eta
        timestamptz completed_at
    }
    route_positions {
        uuid id PK
        uuid route_id FK
        decimal latitude
        decimal longitude
        decimal accuracy_meters
        decimal speed_kmh
        timestamptz recorded_at
    }
    vehicle_preferences {
        uuid user_id PK_FK
        varchar vehicle_type
        varchar fuel_type
        double average_consumption
        timestamptz created_at
        timestamptz updated_at
    }
    co2_savings {
        uuid id PK
        uuid user_id FK
        double distance_optimized
        double distance_non_optimized
        double co2_saved
        varchar vehicle_type
        varchar fuel_type
        double average_consumption
        timestamptz created_at
    }
    co2_emissions {
        uuid id PK
        uuid vehicle_preference_user_id FK
        double route_distance_km
        double co2_emission
        varchar vehicle_type
        varchar fuel_type
        double average_consumption
        timestamptz created_at
    }
    notifications {
        uuid id PK
        uuid user_id FK
        varchar title
        text message
        varchar type
        varchar reference_type
        uuid reference_id
        jsonb metadata
        boolean is_read
        timestamptz created_at
        timestamptz read_at
    }
    fcm_tokens {
        uuid id PK
        uuid user_id FK
        text token
        timestamptz updated_at
    }
    payment_methods {
        uuid id PK
        uuid farmer_id FK
        varchar type
        varchar pix_key_type
        varchar pix_key
        varchar bank_name
        varchar agency
        varchar account_number
        varchar account_type
        varchar holder_name
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    users ||--o{ addresses : "has"
    users ||--|| farmers : "is"
    users ||--|| producer_profiles : "has"
    users ||--|| customers : "is"
    users ||--|| vehicle_preferences : "has"
    users ||--o{ co2_savings : "has"
    users ||--o{ notifications : "has"
    users ||--o{ fcm_tokens : "has"
    farmers ||--o{ farmer_availability : "has"
    farmers ||--o{ products : "sells"
    farmers ||--o{ delivery_routes : "creates"
    farmers ||--o{ payment_methods : "has"
    farmers ||--o{ carts : "receives from"
    farmers ||--o{ orders : "receives"
    customers ||--o{ carts : "has"
    customers ||--o{ orders : "places"
    customers ||--o{ review : "writes"
    customers ||--o{ favorites : "has"
    products ||--o{ product_photos : "has"
    products ||--o{ product_category_assignments : "belongs to"
    products ||--o{ stock_movements : "tracks"
    products ||--o{ cart_items : "is in"
    products ||--o{ order_items : "is in"
    product_categories ||--o{ product_category_assignments : "has"
    carts ||--o{ cart_items : "contains"
    orders ||--o{ order_items : "contains"
    orders ||--o{ order_status_history : "tracks"
    orders ||--|| review : "has"
    orders ||--o| route_stops : "has"
    payment_methods ||--o{ orders : "used in"
    addresses ||--o{ orders : "delivered to"
    delivery_routes ||--o{ route_stops : "has"
    delivery_routes ||--o{ route_positions : "has"
    vehicle_preferences ||--o{ co2_emissions : "used in"

---

## Domains

| Domain | Tables |
|--------|--------|
| 👤 Users and Profiles | `users` `farmers` `producer_profiles` `customers` `addresses` `farmer_availability` `notifications` `fcm_tokens` |
| 📦 Products and Inventory | `products` `product_categories` `product_category_assignments` `product_photos` `stock_movements` |
| 🛒 Cart and Orders | `carts` `cart_items` `orders` `order_items` `order_status_history` |
| ⭐ Reviews and Favorites | `review` `favorites` |
| 🚚 Logistics | `delivery_routes` `route_stops` `route_positions` |
| 🌱 Sustainability (CO2) | `vehicle_preferences` `co2_savings` `co2_emissions` |
| 💳 Payment | `payment_methods` |

---

## Tables

### Users and Profiles

#### `users`
Base authentication table shared across all user types.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Automatically generated primary key |
| `name` | varchar(120) | ✅ | User’s full name |
| `email` | varchar(254) | ✅ | Unique email — used as login |
| `phone` | varchar(20) | ❌ | Contact phone number |
| `type` | varchar(20) | ✅ | User role: `farmer` \| `customer` \| `admin` |
| `active` | boolean | ✅ | `false` = account disabled, prevents system access |
| `auth_sub` | text | ✅ | Unique identifier from Keycloak. Links the JWT token to the database record |
| `created_at` | timestamptz | ✅ | Record creation timestamp |
| `updated_at` | timestamptz | ✅ | Last update timestamp |

> **Note:** The `auth_sub` acts as the bridge between the authentication system (Keycloak) and the database. When the user logs in, the backend reads the `sub` from the JWT token and fetches the corresponding record using `WHERE auth_sub = ?`.

---

#### `farmers`
Extended profile for farmers. The `id` is the same as `users.id` — a 1:1 relationship.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | FK → `users.id` — same identifier |
| `fiscal_number` | varchar(14) | ✅ | CPF (11 digits) or CNPJ (14 digits) |
| `fiscal_number_type` | varchar(5) | ✅ | Document type: `cpf` \| `cnpj` |
| `farm_name` | varchar(150) | ✅ | Farm name displayed in the marketplace |
| `description` | text | ❌ | Short description shown on marketplace cards |
| `avatar_s3` | text | ❌ | Profile picture URL stored in S3 |
| `display_photo_s3` | text | ❌ | Cover photo URL stored in S3 |
| `total_reviews` | integer | ✅ | Denormalized counter — updated after each review |
| `average_rating` | decimal(3,2) | ✅ | Denormalized rating average — updated after each review |
| `total_orders` | integer | ✅ | Total delivered orders — used in financial dashboard |
| `total_sales_amount` | decimal(14,2) | ✅ | Total revenue in BRL — used in financial dashboard |
| `created_at` | timestamptz | ✅ | Record creation timestamp |
| `updated_at` | timestamptz | ✅ | Last update timestamp |

> **Note:** The fields `total_reviews`, `average_rating`, `total_orders`, and `total_sales_amount` are intentionally denormalized to avoid expensive `COUNT`/`AVG` queries on every profile load. Maintaining consistency of these values is the responsibility of the application layer when processing orders and reviews.

---

#### `producer_profiles`
Detailed profiles/narratives for farmers. The `id` is the same as `users.id` — a 1:1 relationship.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | FK → `users.id` — same identifier |
| `story` | text | ❌ | Full story/narrative displayed on the detailed profile |
| `photo_url` | text | ❌ | Optional banner/profile image URL |
| `member_since` | date | ✅ | Date when the farmer joined the platform |
| `created_at` | timestamptz | ✅ | Record creation timestamp |
| `updated_at` | timestamptz | ✅ | Last update timestamp |

---

#### `customers`
Extended profile for customers. The `id` is the same as `users.id` — a 1:1 relationship.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | FK → `users.id` — same identifier |
| `fiscal_number` | char(11) | ✅ | Customer CPF — 11 digits, unique in the system |
| `created_at` | timestamptz | ✅ | Record creation timestamp |
| `updated_at` | timestamptz | ✅ | Last update timestamp |

---

#### `addresses`
User addresses. A user can have multiple; `is_primary` identifies the main one.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `user_id` | uuid | ✅ | FK → `users.id` |
| `street` | varchar(200) | ✅ | Street |
| `number` | varchar(10) | ✅ | Number |
| `complement` | varchar(100) | ❌ | Complement |
| `neighborhood` | varchar(100) | ✅ | Neighborhood |
| `city` | varchar(100) | ✅ | City |
| `state` | char(2) | ✅ | State (UF) — two characters |
| `zip_code` | char(8) | ✅ | ZIP code — exactly 8 digits, no hyphen |
| `latitude` | decimal(10,7) | ❌ | Latitude geocoded via maps API |
| `longitude` | decimal(10,7) | ❌ | Longitude geocoded via maps API |
| `geocode_status` | varchar(12) | ❌ | Geocoding outcome: `OK` \| `AMBIGUOUS` \| `FAILED` — `null` = never attempted |
| `geocoded_at` | timestamptz | ❌ | When geocoding was last attempted |
| `is_primary` | boolean | ✅ | `true` = user's primary address |
| `created_at` | timestamptz | ✅ | Record creation timestamp |

> **Note:** `latitude` and `longitude` are filled by geocoding the address via the Google Maps API. `geocode_status` and `geocoded_at` track the last attempt: a `null` status means it was never tried (re-attempted on next use), so failed/ambiguous addresses self-heal instead of being geocoded once at registration. Once resolved, proximity queries use the stored coordinates directly — avoiding additional API costs per query.

---

#### `farmer_availability`
Farmer availability hours by weekday. Displayed on the public profile.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `farmer_id` | uuid | ✅ | FK → `farmers.id` |
| `weekday` | smallint | ✅ | Day of the week: `0`=Sunday, `1`=Monday, ..., `6`=Saturday |
| `opens_at` | time | ✅ | Opening time |
| `closes_at` | time | ✅ | Closing time |
| `active` | boolean | ✅ | Allows disabling a day without removing the record |

**Unique index:** `(farmer_id, weekday)` — only one schedule per day per farmer.

---

#### `notifications`
Stores system and order-related notifications targeted at users.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `user_id` | uuid | ✅ | FK → `users.id` — target user for the notification |
| `title` | varchar(120) | ✅ | Notification title |
| `message` | text | ✅ | Notification body message |
| `type` | varchar(40) | ✅ | Notification category/type |
| `reference_type` | varchar(40) | ❌ | Type of reference entity (e.g. `order`, `product`) |
| `reference_id` | uuid | ❌ | ID of reference entity |
| `metadata` | jsonb | ❌ | Additional JSON metadata |
| `is_read` | boolean | ✅ | `true` if read, `false` otherwise |
| `created_at` | timestamptz | ✅ | Notification creation timestamp |
| `read_at` | timestamptz | ❌ | Timestamp when user marked notification as read |

---

#### `fcm_tokens`
Firebase Cloud Messaging device tokens used to deliver push notifications. A user may have multiple devices/tokens.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `user_id` | uuid | ✅ | FK → `users.id` — token owner |
| `token` | text | ✅ | FCM registration token — UNIQUE across the system |
| `updated_at` | timestamptz | ✅ | Last time the token was registered/refreshed |

**Index:** `(user_id)` — lookups of all tokens for a user when sending a push.

---

### Products and Inventory

#### `products`

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `farmer_id` | uuid | ✅ | FK → `farmers.id` — product belongs to a farmer |
| `name` | varchar(150) | ✅ | Product name displayed in the marketplace |
| `description` | text | ❌ | Detailed product description |
| `price` | decimal(10,2) | ✅ | Current unit price in BRL |
| `unity_type` | varchar(20) | ✅ | Unit: `kg` \| `g` \| `unit` \| `box` \| `liter` \| `ml` \| `dozen` |
| `stock_quantity` | decimal(12,3) | ✅ | Current available stock — decremented when order is confirmed |
| `image_s3` | text | ❌ | Main image URL stored in S3 |
| `active` | boolean | ✅ | `false` = product hidden from marketplace (soft delete) |
| `version` | bigint | ✅ | JPA optimistic-lock version — guards concurrent stock writes (`registerSale`/`registerCancelledSale`); conflicts raise `409` |
| `created_at` | timestamptz | ✅ | Record creation timestamp |
| `updated_at` | timestamptz | ✅ | Last update timestamp |

> **Note:** When a product is deactivated, the trigger `trg_product_deactivated` automatically disables all `cart_items` associated with that product and any carts that end up with no active items.

---

#### `product_categories`

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | serial | ✅ | Auto-incremented primary key |
| `name` | varchar(80) | ✅ | Unique category name — e.g., Vegetables, Fruits |
| `description` | text | ❌ | Category description |

---

#### `product_category_assignments`
Junction table (N:N) between products and categories. A product can belong to multiple categories.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `product_id` | uuid | ✅ | FK → `products.id` — part of the composite primary key |
| `category_id` | integer | ✅ | FK → `product_categories.id` — part of the composite primary key |

---

#### `product_photos`
Photo gallery per product. Display order is controlled by `display_order`.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `product_id` | uuid | ✅ | FK → `products.id` |
| `url` | text | ✅ | Photo URL stored in S3 |
| `display_order` | smallint | ✅ | Display order — lower values appear first |
| `created_at` | timestamptz | ✅ | Record creation timestamp |

---

#### `stock_movements`
Log of all stock movements. Insert-only log of stock history.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `product_id` | uuid | ✅ | FK → `products.id` |
| `type` | varchar(10) | ✅ | Direction: `ENTRY` (incoming) \| `EXIT` (outgoing) |
| `reason` | varchar(20) | ✅ | Reason: `SALE` \| `LOSS` \| `DISPOSAL` \| `MANUAL_ENTRY` \| `CANCELED_SALE` |
| `quantity` | decimal(12,3) | ✅ | Quantity moved |
| `notes` | text | ❌ | Optional note from the farmer |
| `created_at` | timestamptz | ✅ | Movement creation timestamp |
| `updated_at` | timestamptz | ✅ | Last update timestamp |

> **Note:** Every change to `stock_quantity` in `products` must generate a record here. This enables full stock auditing and powers the movement history for Epic 5.

---

### Cart and Orders

#### `carts`
Active cart for the customer. A `UNIQUE` index on `customer_id` ensures one cart per customer.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `customer_id` | uuid | ✅ | FK → `customers.id` — UNIQUE, one cart per customer |
| `farmer_id` | uuid | ✅ | FK → `farmers.id` — enforces the rule of one farmer per cart |
| `active` | boolean | ✅ | `false` = cart emptied or deactivated by trigger |
| `created_at` | timestamptz | ✅ | Record creation timestamp |
| `updated_at` | timestamptz | ✅ | Last update timestamp |

> **Note:** The one-farmer-per-cart rule is enforced by the `farmer_id` field. When attempting to add a product from another farmer, the application should warn the user and, if confirmed, clear the current cart before creating a new one.

---

#### `cart_items`

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `cart_id` | uuid | ✅ | FK → `carts.id` |
| `product_id` | uuid | ✅ | FK → `products.id` |
| `quantity` | decimal(12,3) | ✅ | Quantity selected by the customer |
| `active` | boolean | ✅ | `false` = item removed or product deactivated |

**Unique index:** `(cart_id, product_id)` — no duplicate items in the same cart.

---

#### `orders`
Order generated from the cart. Immutable after creation — status changes are tracked in `order_status_history`.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `customer_id` | uuid | ✅ | FK → `customers.id` |
| `farmer_id` | uuid | ✅ | FK → `farmers.id` |
| `delivery_address_id` | uuid | ✅ | FK → `addresses.id` — current address |
| `delivery_address_snapshot` | jsonb | ✅ | Copy of the address at order time — immutable |
| `status` | varchar(20) | ✅ | `pending` \| `confirmed` \| `delivering` \| `delivered` \| `cancelled` |
| `payment_method_id` | uuid | ✅ | FK → `payment_methods.id` |
| `payment_status` | varchar(20) | ✅ | `pending` \| `paid` \| `refunded` |
| `scheduled_for` | timestamptz | ❌ | Scheduled delivery date and time |
| `delivered_at` | timestamptz | ❌ | Actual delivery date and time |
| `notes` | text | ❌ | Customer notes |
| `cancellation_reason` | text | ❌ | Short reason or code for the cancellation (e.g. `CUSTOMER_GIVE_UP`) |
| `cancellation_details` | text | ❌ | Longer justification/details optionally provided upon cancellation |
| `confirmation_code` | varchar(4) | ❌ | 4-digit delivery confirmation code generated when the order enters delivery; `CHECK` enforces exactly 4 digits |
| `confirmation_attempts` | integer | ✅ | Failed confirmation-code attempts — defaults to `0` (brute-force protection) |
| `confirmation_locked_until` | timestamptz | ❌ | Lockout timestamp after too many failed confirmation attempts |
| `seen_by_farmer` | boolean | ✅ | `true` if the farmer has viewed the order, `false` otherwise |
| `created_at` | timestamptz | ✅ | Record creation timestamp |
| `updated_at` | timestamptz | ✅ | Last update timestamp |

> **Note:** `delivery_address_snapshot` exists because the customer may change their address after placing the order. The snapshot ensures the history reflects where the delivery was actually intended.

---

#### `order_items`
Order items with snapshots of product data at the time of purchase.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `order_id` | uuid | ✅ | FK → `orders.id` |
| `product_id` | uuid | ✅ | FK → `products.id` — current reference |
| `product_name_snapshot` | varchar(150) | ✅ | Product name at the time of purchase |
| `unit_price_snapshot` | decimal(10,2) | ✅ | Unit price at the time of purchase |
| `unity_type_snapshot` | varchar(20) | ✅ | Unit of measure at the time of purchase |
| `quantity` | decimal(12,3) | ✅ | Quantity purchased |
| `subtotal` | decimal(12,2) | ✅ | `quantity × unit_price_snapshot` |

> **Note:** The three snapshot fields ensure that order history remains accurate even if the farmer later changes the product name, price, or unit.

---

#### `order_status_history`
Immutable log of all order status transitions. Insert-only.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `order_id` | uuid | ✅ | FK → `orders.id` |
| `status` | varchar(20) | ✅ | Status recorded in this transition |
| `changed_at` | timestamptz | ✅ | Timestamp of the status change |

---

### Reviews and Favorites

#### `review`
One review per order — enforced by a `UNIQUE` constraint on `order_id`. Can only be created for orders with status `delivered`.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `order_id` | uuid | ✅ | FK → `orders.id` — UNIQUE, one review per order |
| `farmer_id` | uuid | ✅ | FK → `farmers.id` — reviewed farmer |
| `customer_id` | uuid | ✅ | FK → `customers.id` — review author |
| `rating` | smallint | ✅ | Rating from 1 to 5 |
| `comment` | text | ❌ | Optional comment |
| `created_at` | timestamptz | ✅ | Review timestamp |

> **Note:** After each insert into `review`, the application must recalculate `average_rating` and increment `total_reviews` in `farmers` to keep the denormalized fields consistent.

---

#### `favorites`
Junction table between customers and their favorite farmers. Composite primary key prevents duplicates.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `customer_id` | uuid | ✅ | FK → `customers.id` — part of the composite primary key |
| `farmer_id` | uuid | ✅ | FK → `farmers.id` — part of the composite primary key |
| `created_at` | timestamptz | ✅ | Timestamp when the farmer was favorited |

---

### Logistics

#### `delivery_routes`
Persisted delivery route by the farmer, computed via the Google Routes API. Can include multiple orders. A partial unique index enforces at most one `ACTIVE` route per farmer (creating a new one replaces the previous).

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `farmer_id` | uuid | ✅ | FK → `farmers.id` |
| `status` | varchar(20) | ✅ | `ACTIVE` \| `COMPLETED` \| `CANCELLED` (defaults to `ACTIVE`) |
| `origin_latitude` | decimal(10,7) | ✅ | Origin latitude (farmer's location) |
| `origin_longitude` | decimal(10,7) | ✅ | Origin longitude (farmer's location) |
| `total_distance_km` | decimal(10,2) | ❌ | Total optimized road distance returned by the Routes API |
| `total_duration_seconds` | integer | ❌ | Total estimated duration in seconds |
| `baseline_distance_km` | decimal(10,2) | ❌ | CO2 baseline: sum of individual round-trips to each stop (Route Matrix) |
| `overview_polyline` | text | ❌ | Encoded polyline used to render the route on the map |
| `created_at` | timestamptz | ✅ | Record creation timestamp |
| `completed_at` | timestamptz | ❌ | When the route was completed |

**Indexes:**
- `(farmer_id)` — list routes per farmer
- `(farmer_id) WHERE status = 'ACTIVE'` (partial UNIQUE) — at most one active route per farmer

---

#### `route_stops`
Each ordered stop in the route, 1:1 with the order delivered at it. An order can only belong to one stop within a route — enforced by `UNIQUE (route_id, order_id)`.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `route_id` | uuid | ✅ | FK → `delivery_routes.id` (ON DELETE CASCADE) |
| `order_id` | uuid | ✅ | FK → `orders.id` |
| `sequence` | integer | ✅ | Visit position in the optimized route (0-based) |
| `status` | varchar(20) | ✅ | `PENDING` \| `ARRIVED` \| `DELIVERED` \| `FAILED` (defaults to `PENDING`) |
| `latitude` | decimal(10,7) | ✅ | Stop latitude |
| `longitude` | decimal(10,7) | ✅ | Stop longitude |
| `address_text` | text | ❌ | Human-readable address of the stop |
| `leg_distance_km` | decimal(10,2) | ❌ | Distance of the leg arriving at this stop (from previous stop or origin) |
| `leg_duration_seconds` | integer | ❌ | Duration of the leg arriving at this stop |
| `eta` | timestamptz | ❌ | Absolute ETA estimated at route creation |
| `completed_at` | timestamptz | ❌ | When this stop was completed |

**Unique indexes:**
- `(route_id, sequence)` — no duplicate stop positions within the same route
- `(route_id, order_id)` — the same order cannot appear twice in a route

---

#### `route_positions`
Real-time GPS trail of the producer during an active route. Personal location data with 7-day retention, purged by a daily job.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `route_id` | uuid | ✅ | FK → `delivery_routes.id` (ON DELETE CASCADE) |
| `latitude` | decimal(10,7) | ✅ | Recorded latitude |
| `longitude` | decimal(10,7) | ✅ | Recorded longitude |
| `accuracy_meters` | decimal(8,2) | ❌ | GPS accuracy in meters |
| `speed_kmh` | decimal(6,2) | ❌ | Speed in km/h at the time of the reading |
| `recorded_at` | timestamptz | ✅ | When the position was recorded |

**Indexes:**
- `(route_id, recorded_at DESC)` — latest positions for a route's live trail
- `(recorded_at)` — used by the 7-day retention purge

---

### Sustainability (CO2)

#### `vehicle_preferences`
Stores the vehicle/fuel configuration a user last used for CO2 calculations, so it can be pre-filled on subsequent calculations. One row per user.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `user_id` | uuid | ✅ | Primary key — FK → `users.id` (1:1) |
| `vehicle_type` | varchar(50) | ✅ | `MOTORCYCLE` \| `CAR` \| `VAN` \| `LIGHT_TRUCK` |
| `fuel_type` | varchar(50) | ✅ | `GASOLINE` \| `ETHANOL` \| `DIESEL` \| `ELECTRIC` |
| `average_consumption` | double precision | ✅ | Average consumption (km/L) used in the emission formula |
| `created_at` | timestamptz | ✅ | Record creation timestamp |
| `updated_at` | timestamptz | ✅ | Last update timestamp |

---

#### `co2_savings`
Logs CO2 saved by choosing an optimized route over a non-optimized one.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `user_id` | uuid | ✅ | FK → `users.id` |
| `distance_optimized` | double precision | ✅ | Distance (km) of the optimized route |
| `distance_non_optimized` | double precision | ✅ | Distance (km) of the non-optimized route |
| `co2_saved` | double precision | ✅ | kg of CO2 saved (non-optimized − optimized) |
| `vehicle_type` | varchar(50) | ✅ | Snapshot of the vehicle type used |
| `fuel_type` | varchar(50) | ✅ | Snapshot of the fuel type used |
| `average_consumption` | double precision | ✅ | Snapshot of the average consumption used |
| `created_at` | timestamptz | ✅ | Record creation timestamp |

---

#### `co2_emissions`
Logs each CO2 emission computed for a route, linked to the vehicle preference used in the calculation. Emission (kg) = `route_distance_km / average_consumption × fuel emission factor`; `0` for electric vehicles.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `vehicle_preference_user_id` | uuid | ✅ | FK → `vehicle_preferences.user_id` — the vehicle config used for this calculation |
| `route_distance_km` | double precision | ✅ | Route distance (km) used in the calculation |
| `co2_emission` | double precision | ✅ | kg of CO2 emitted for the route |
| `vehicle_type` | varchar(50) | ✅ | Snapshot of the vehicle type at calculation time |
| `fuel_type` | varchar(50) | ✅ | Snapshot of the fuel type at calculation time |
| `average_consumption` | double precision | ✅ | Snapshot of the average consumption at calculation time |
| `created_at` | timestamptz | ✅ | Record creation timestamp |

**Index:** `(vehicle_preference_user_id)` — lookups of a user's emission history.

---

### Payment

#### `payment_methods`
Payment methods registered by the farmer. Supports PIX and bank accounts.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `farmer_id` | uuid | ✅ | FK → `farmers.id` |
| `type` | varchar(20) | ✅ | Type: `pix` \| `bank_account` |
| `pix_key_type` | varchar(20) | ❌ | PIX key type: `cpf` \| `cnpj` \| `email` \| `phone` \| `random` |
| `pix_key` | varchar(100) | ❌ | PIX key — filled only if `type = pix` |
| `bank_code` | char(3) | ❌ | Bank COMPE code — 3 digits |
| `bank_name` | varchar(100) | ❌ | Bank name |
| `agency` | varchar(10) | ❌ | Branch number |
| `account_number` | varchar(20) | ❌ | Account number |
| `account_type` | varchar(20) | ❌ | Type: `checking` \| `savings` |
| `holder_name` | varchar(120) | ❌ | Account holder name |
| `fiscal_number` | varchar(14) | ❌ | Holder CPF or CNPJ |
| `active` | boolean | ✅ | `false` = method disabled |
| `created_at` | timestamptz | ✅ | Record creation timestamp |
| `updated_at` | timestamptz | ✅ | Last update timestamp |

**Unique index:** `(farmer_id, type, pix_key)` — prevents duplicate registration of the same payment method for the same farmer.

---

## Triggers

### `trg_product_deactivated`
Triggered after an `UPDATE` on the `active` field of `products` when the value changes from `true` to `false`.

**Cascade effect:**
1. Deactivates all `cart_items` where `product_id = deactivated product`
2. Checks each affected cart — if no active items remain, the cart is also deactivated

**Why it exists:** If a farmer deactivates a product that is in a customer’s cart, the item cannot remain there — the customer would attempt to purchase something unavailable. This trigger automatically resolves the issue at the database level, regardless of which part of the system performed the deactivation.

```sql
AFTER UPDATE OF active ON products
FOR EACH ROW
WHEN (OLD.active = true AND NEW.active = false)
-- deactivates cart_items and carts left with no active items
```

---

### `trg_farmer_deactivated`
Triggered after an UPDATE on users when type = 'farmer' and active changes to false.

**Cascade Effect:**
1. Deactivates all cart_items from all carts related to that farmer
2. Deactivates all carts where farmer_id = deactivated farmer

**Why it exists:** When an administrator deactivates a farmer, all customers with carts from that farmer must be affected and their carts cleared. The trigger guarantees this behavior automatically, regardless of which part of the system performed the deactivation.

```sql
AFTER UPDATE OF active ON users
FOR EACH ROW
WHEN (OLD.type = 'farmer')
-- the active true→false check lives inside fn_deactivate_carts_on_farmer(),
-- which deactivates all carts and items related to the farmer
```