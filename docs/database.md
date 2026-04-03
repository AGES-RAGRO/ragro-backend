# RAGRO — Database Documentation

> PostgreSQL 16 · 21 tables · 2 triggers

> **Note — Schema Sync**: This document is the **design reference** for the database. The runtime schema is defined in `data/schema.sql`. If any field documented here is missing from `schema.sql`, it means the migration has not been applied yet. Known pending migrations are listed below.

### Pending Schema Additions

The following fields are documented here but **not yet present** in `data/schema.sql`:

| Table | Column | Type | Status |
|-------|--------|------|--------|
| `farmers` | `story` | `text` | Pending — producer narrative for the profile page |
| `cart_items` | `price_snapshot` | `decimal(10,2)` | Pending — price at the time the item was added |

These must be added to `data/schema.sql` before the corresponding features can be implemented.

---

## Table of Contents

- [Entity-Relationship Diagram](#entity-relationship-diagram)
- [Domains](#domains)
- [Tables](#tables)
  - [Users and Profiles](#users-and-profiles)
  - [Products and Inventory](#products-and-inventory)
  - [Cart and Orders](#cart-and-orders)
  - [Reviews and Favorites](#reviews-and-favorites)
  - [Logistics](#logistics)
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
        text story
        text avatar_s3
        text display_photo_s3
        integer total_reviews
        decimal average_rating
        integer total_orders
        decimal total_sales_amount
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
        varchar city
        char state
        char zip_code
        decimal latitude
        decimal longitude
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
        decimal price_snapshot
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
        date planned_date
        timestamptz started_at
        timestamptz completed_at
        timestamptz created_at
        timestamptz updated_at
    }
    delivery_route_stops {
        uuid id PK
        uuid route_id FK
        uuid order_id FK
        smallint stop_order
        varchar status
        timestamptz delivered_at
        text notes
    }
    visual_route_information {
        uuid id PK
        uuid delivery_route_id FK
        text encoded_polyline
        integer total_distance_meters
        text total_duration
        varchar travel_mode
        decimal origin_latitude
        decimal origin_longitude
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
users ||--|| customers : "is"
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
orders ||--o| delivery_route_stops : "has"
payment_methods ||--o{ orders : "used in"
addresses ||--o{ orders : "delivered to"
delivery_routes ||--o{ delivery_route_stops : "has"
delivery_routes ||--|| visual_route_information : "has"

---

## Domains

| Domain | Tables |
|--------|--------|
| 👤 Users and Profiles | `users` `farmers` `customers` `addresses` `farmer_availability` |
| 📦 Products and Inventory | `products` `product_categories` `product_category_assignments` `product_photos` `stock_movements` |
| 🛒 Cart and Orders | `carts` `cart_items` `orders` `order_items` `order_status_history` |
| ⭐ Reviews and Favorites | `review` `favorites` |
| 🚚 Logistics | `delivery_routes` `delivery_route_stops` `visual_route_information` |
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
| `story` | text | ❌ | Full story displayed on the detailed profile |
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
| `neighborhood` | varchar(100) | ❌ | Neighborhood |
| `city` | varchar(100) | ✅ | City |
| `state` | char(2) | ✅ | State (UF) — two characters |
| `zip_code` | char(8) | ✅ | ZIP code — exactly 8 digits, no hyphen |
| `latitude` | decimal(10,7) | ❌ | Latitude geocoded at registration via maps API |
| `longitude` | decimal(10,7) | ❌ | Longitude geocoded at registration via maps API |
| `is_primary` | boolean | ✅ | `true` = user's primary address |
| `created_at` | timestamptz | ✅ | Record creation timestamp |

> **Note:** `latitude` and `longitude` are filled only once when the address is created, using the Google Maps API or device GPS. From then on, all proximity queries use the database directly — avoiding additional API costs per query.

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
Immutable log of all stock movements. Insert-only — never updated.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `product_id` | uuid | ✅ | FK → `products.id` |
| `type` | varchar(10) | ✅ | Direction: `entry` (incoming) \| `exit` (outgoing) |
| `reason` | varchar(20) | ✅ | Reason: `sale` \| `loss` \| `disposal` \| `manual_entry` |
| `quantity` | decimal(12,3) | ✅ | Quantity moved |
| `notes` | text | ❌ | Optional note from the farmer |
| `created_at` | timestamptz | ✅ | Movement timestamp |

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
| `price_snapshot` | decimal(10,2) | ✅ | Price at the moment the item was added to the cart |
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
| `cancellation_reason` | text | ❌ | Filled only when `status = cancelled` |
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
Represents a delivery trip by the farmer. Can include multiple orders.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `farmer_id` | uuid | ✅ | FK → `farmers.id` |
| `status` | varchar(20) | ✅ | `planned` \| `in_progress` \| `completed` \| `cancelled` |
| `planned_date` | date | ✅ | Planned date for the delivery trip |
| `started_at` | timestamptz | ❌ | When the farmer started the route |
| `completed_at` | timestamptz | ❌ | When the route was completed |
| `created_at` | timestamptz | ✅ | Record creation timestamp |
| `updated_at` | timestamptz | ✅ | Last update timestamp |

---

#### `delivery_route_stops`
Each stop in the route. An order can only belong to one route at a time — enforced by `UNIQUE` on `order_id`.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `route_id` | uuid | ✅ | FK → `delivery_routes.id` |
| `order_id` | uuid | ✅ | FK → `orders.id` — UNIQUE, an order can be in at most one route |
| `stop_order` | smallint | ✅ | Visit sequence in the route — defined by the maps API |
| `status` | varchar(20) | ✅ | `pending` \| `arrived` \| `delivered` \| `failed` |
| `delivered_at` | timestamptz | ❌ | Actual delivery timestamp at this stop |
| `notes` | text | ❌ | Delivery notes |

**Unique indexes:**
- `(route_id, stop_order)` — no duplicate stop numbers within the same route  
- `(order_id)` — the same order cannot appear in multiple routes

---

#### `visual_route_information`
Stores data returned by the Google Maps Directions API for a route. Avoids recalculating every time the farmer opens the app.

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | uuid | ✅ | Primary key |
| `delivery_route_id` | uuid | ✅ | FK → `delivery_routes.id` |
| `encoded_polyline` | text | ❌ | Encoded polyline used to render the route on the map |
| `total_distance_meters` | integer | ❌ | Total route distance in meters |
| `total_duration` | text | ❌ | Estimated total duration — e.g., `1h 23min` |
| `optimized_stop_order` | smallint[] | ❌ | Array with optimized stop sequence returned by the API |
| `travel_mode` | varchar(20) | ❌ | Transport mode: `driving` \| `walking` \| `bicycling` |
| `origin_latitude` | decimal(10,7) | ❌ | Origin latitude (farmer’s location) |
| `origin_longitude` | decimal(10,7) | ❌ | Origin longitude (farmer’s location) |

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
WHEN (OLD.type = 'farmer' AND OLD.active = true AND NEW.active = false)
-- deactivates all carts and items related to the farmer
```