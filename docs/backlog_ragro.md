**RAGRO**  
From the Farm to the Table

*Product Backlog — Revised Version*

# **API Routes Summary**

## **Authentication**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /auth/register/customer | Customer registration |
| POST | /auth/register/producer | Producer registration (admin) |
| POST | /auth/login/customer | Customer login |
| POST | /auth/login/producer | Producer login |

## **Customers**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /customers | List all customers (admin) |
| GET | /customers/:id | Retrieve customer profile |
| PUT | /customers/:id | Update customer profile |

## **Producers**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /producers | List active producers (marketplace) |
| GET | /producers/:id | Retrieve producer profile + products |
| PUT | /producers/:id | Update producer profile |
| GET | /producers/:id/reviews | List producer reviews |

## **Administration**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /admin/producers | Register producer |
| GET | /admin/producers | List producers (admin) |
| GET | /admin/producers/:id | Producer details (admin) |
| PATCH | /admin/producers/:id/deactivate | Deactivate producer |
| PATCH | /admin/producers/:id/activate | Reactivate producer |

## **Products**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /products | Create product |
| GET | /products/:id | Retrieve product |
| PUT | /products/:id | Edit product |
| DELETE | /products/:id | Delete product |

## **Stock**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /stock/entry | Register stock entry |
| POST | /stock/exit | Register stock exit |
| GET | /stock/:productId/movements | Product movement history |

## **Cart**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /cart | Retrieve active cart |
| POST | /cart/items | Add item to cart |
| PUT | /cart/items/:id | Update item quantity |
| DELETE | /cart/items/:id | Remove item from cart |

## **Orders**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /orders | Create order from cart |
| GET | /orders/customer | Customer order history |
| GET | /orders/producer | Orders received by producer |
| GET | /orders/today | Today's orders (producer) |
| PATCH | /orders/:id/confirm | Confirm order (producer) |
| PATCH | /orders/:id/cancel | Cancel order (customer) |
| PATCH | /orders/:id/status | Update delivery status (producer) |
| POST | /orders/:id/repeat | Repeat previous order |
| POST | /orders/:id/schedule | Schedule order for a future date |

## **Reviews**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /reviews | Create review for delivered order |

## **Logistics**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /logistics/route | Generate optimized daily route (Google Maps) |

## **Recommendations**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /recommendations | Product suggestions for the customer |

## **Financial Dashboard**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /dashboard/producer/summary | Producer financial summary |
| GET | /dashboard/producer/sales | Producer sales history |

# **EPIC 1 — Authentication and User Management**

Objective: Enable secure authentication, role-based access control, and lifecycle management for customer, producer, and admin accounts.  
Recommended technology: Keycloak 26, JWT

## **Entity Modeling**

### **User**

| Field | Type | Notes |
| :---- | :---- | :---- |
| id | UUID | Primary key |
| email | string | Unique, used as login |
| role | enum | customer \| producer \| admin |
| active | boolean | Access control |
| authSub | string | Keycloak subject identifier |
| createdAt | timestamp |  |
| updatedAt | timestamp |  |

### **CustomerProfile**

| Field | Type | Notes |
| :---- | :---- | :---- |
| id | UUID | Primary key |
| userId | UUID FK | Reference to User |
| name | string | Full name |
| phone | string | Contact phone |
| addressId | UUID FK | Reference to Address |

### **ProducerProfile**

| Field | Type | Notes |
| :---- | :---- | :---- |
| id | UUID | Primary key |
| userId | UUID FK | Reference to User |
| name | string | Producer / company name |
| phone | string |  |
| cpfCnpj | string | Mandatory validation |
| description | string | Marketplace presentation |
| story | text | Producer story (long narrative) |
| photoUrl | string | Profile photo URL |
| location | string | Production city/region |
| bankData | jsonb | Encrypted bank details |
| addressId | UUID FK | Reference to Address |
| rating | decimal | Calculated average from reviews |
| memberSince | timestamp | Platform registration date |

### **Address**

| Field | Type | Notes |
| :---- | :---- | :---- |
| id | UUID | Primary key |
| street | string |  |
| number | string |  |
| complement | string | Optional |
| neighborhood | string |  |
| city | string |  |
| state | string |  |
| zipCode | string |  |
| latitude | decimal | For geolocation and routing |
| longitude | decimal | For geolocation and routing |

## **US-01 — Customer Registration**

As a customer, I want to create an account to purchase products on the platform.

**Acceptance criteria:**

* User must provide: name, phone number, email, password, and address  
* Email must be unique in the system  
* Password must comply with Keycloak's password policy  
* Address must be saved and linked to the CustomerProfile  
* User must be created in Keycloak and in the application database  
* Customer must be assigned to the 'customer' group in Keycloak  
* Registration must allow future profile editing

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /auth/register/customer | Creates customer user |

**Features / Technical Tasks:**

### **FE-01 — Configure Keycloak Realm**

* Create Keycloak realm `ragro` (pre-configured via `keycloak/ragro-realm.json`)  
* Configure required attributes  
* Configure password policy  
* Configure email as login  
* Create groups: CUSTOMER, FARMER, ADMIN  
* Configure client `ragro-app` with Direct Access Grants

### **FE-02 — Role and Group Control in Keycloak**

* Create CUSTOMER, FARMER, and ADMIN groups  
* Configure group membership mapper for `groups` JWT claim  
* Map groups to application roles

### **FE-03 — User Entity Modeling**

* Create User entity  
* Create CustomerProfile entity  
* Create Address entity (with latitude/longitude)  
* Create migrations  
* Create repositories  
* Link User → CustomerProfile → Address

### **FE-04 — Customer Registration Endpoint**

* Create registration DTO  
* Create POST /auth/register/customer endpoint  
* Validate required fields  
* Integrate with Keycloak Admin API to create user  
* Persist user in the database  
* Assign user to the customer group in Keycloak  
* Persist CustomerProfile and Address

### **FE-05 — Mobile Registration Screen**

* Create registration screen  
* Create form with all required fields  
* Validate required fields  
* Integrate with API  
* Handle registration errors

## **US-02 — Customer Login**

As a customer, I want to log in to access my account.

**Acceptance criteria:**

* Login via email and password  
* Authentication via Keycloak  
* System must return JWT with role and userId  
* Error on invalid credentials  
* Authenticated customer can only access features permitted for their profile  
* Inactive customer cannot access the application

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /auth/login/customer | Authenticates customer and returns JWT |

### **FE-06 — Keycloak Authentication Integration**

* Configure Keycloak Direct Access Grants login flow  
* Implement POST /auth/login/customer endpoint  
* Return access token

### **FE-07 — Authentication Middleware**

* Create JWT middleware  
* Validate Keycloak token  
* Extract userId and role from token  
* Protect private routes  
* Validate whether user is active in the database

### **FE-08 — Mobile Login Screen**

* Create login screen  
* Create email/password form  
* Integrate with API  
* Store token locally (secure storage)

## **US-03 — Retrieve Customer Profile**

As a customer, I want to view my registration data to check my account information.

**Acceptance criteria:**

* Authenticated user can view: name, phone number, email, and address  
* Data must come from the application database

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /customers | Lists all customers (admin) |
| GET | /customers/:id | Returns customer profile |

### **FE-09 — Profile Retrieval Endpoint**

* Create GET /customers endpoint (admin)  
* Create GET /customers/:id endpoint  
* Validate authentication  
* Build response with User, CustomerProfile, and Address data

### **FE-10 — Mobile Profile Screen**

* Create profile screen  
* Display user data  
* Integrate with API

## **US-04 — Update Customer Profile**

As a customer, I want to edit my data to keep my profile up to date.

**Acceptance criteria:**

* Allow editing of: name, phone number, and address  
* User must be authenticated  
* Data must be persisted in the database

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| PUT | /customers/:id | Updates customer profile |

### **FE-11 — Profile Update Endpoint**

* Create PUT /customers/:id endpoint  
* Validate authentication  
* Update CustomerProfile and Address in the database

### **FE-12 — Mobile Edit Screen**

* Create profile edit screen  
* Pre-fill with current data  
* Allow editing  
* Integrate with API

## **US-05 — Producer Registration by Admin**

As an administrator, I want to register approved producers to allow them to sell on the platform.

**Acceptance criteria:**

* Only admin can register producers  
* Required fields: name, CPF/CNPJ, phone, email, address, location, description, bank details  
* Optional fields: producer story, profile photo (photoUrl)  
* Producer must be created in Keycloak and in the database  
* Producer must be assigned to the 'producer' group in Keycloak  
* Producer must be able to access the platform immediately after registration

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /admin/producers | Creates producer (admin only) |

### **FE-13 — Producer Entity Modeling**

* Create ProducerProfile entity with fields: story, photoUrl, rating, memberSince  
* Link ProducerProfile to User  
* Reuse Address entity (with latitude/longitude)  
* Create migrations and repositories

### **FE-14 — Producer Registration Endpoint**

* Create POST /admin/producers endpoint  
* Validate admin role  
* Create Keycloak user  
* Save ProducerProfile in the database  
* Assign to producer group in Keycloak  
* Persist bank details  
* Validate CPF/CNPJ  
* Validate unique email

### **FE-15 — Admin Producer Registration Screen**

* Create producer registration screen  
* Create form with all fields  
* Integrate with API

## **US-06 — Retrieve Producer Data**

As a producer, I want to view my registration data to check my account information.

**Acceptance criteria:**

* Authenticated producer can view: name, phone, email, address, location, description, bank details, story, and photo

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /producers/:id | Returns complete producer profile |

### **FE-16 — Producer Profile Retrieval Endpoint**

* Create GET /producers/:id endpoint  
* Validate authentication  
* Build consolidated response (ProducerProfile + Address + bank details)

### **FE-17 — Mobile Producer Profile Screen**

* Create producer profile screen  
* Display all data  
* Integrate with API

## **US-07 — Update Producer Data**

As a producer, I want to edit my registration data to keep my profile up to date.

**Acceptance criteria:**

* Allow editing of: phone, address, location, description, bank details, story, and photo (photoUrl)  
* Data must be persisted  
* Producer must be authenticated

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| PUT | /producers/:id | Updates producer profile |

### **FE-18 — Producer Profile Update Endpoint**

* Create PUT /producers/:id endpoint  
* Validate authentication  
* Update ProducerProfile, Address, and optional fields (story, photoUrl) in the database

### **FE-19 — Producer Edit Screen**

* Create edit form  
* Integrate with API  
* Validate required fields

## **US-08 — Producer Login**

As a producer, I want to access my account to manage my products.

**Acceptance criteria:**

* Login via email and password  
* Authentication via Keycloak  
* Token must contain the producer role  
* Producer can only access features permitted for their profile  
* Inactive producer cannot access the application

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /auth/login/producer | Authenticates producer and returns JWT |

### **FE-20 — Keycloak Authentication Integration (Producer)**

* Configure Keycloak login flow for producer  
* Implement POST /auth/login/producer endpoint  
* Return access token

### **FE-21 — Producer Authentication Middleware**

* Validate Keycloak token  
* Extract userId and role from token  
* Protect private producer routes  
* Validate whether producer is active in the database

### **FE-22 — Mobile Producer Login Screen**

* Create login screen  
* Create email/password form  
* Integrate with API  
* Store token locally

## **US-09 — List Producers in Admin Module**

As an administrator, I want to view registered producers to manage who can operate on the platform.

**Acceptance criteria:**

* Admin can view a list of producers with: name, email, phone, and active/inactive status

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /admin/producers | Lists all producers (admin) |
| GET | /admin/producers/:id | Producer details (admin) |

### **FE-23 — Admin Producer Listing**

* Create GET /admin/producers endpoint  
* Create GET /admin/producers/:id endpoint  
* Create admin listing screen  
* Display producer status

## **US-10 — Deactivate Producer by Admin**

As an administrator, I want to deactivate producers to block their access to the platform.

**Acceptance criteria:**

* Only admin can deactivate a producer  
* Deactivated producer cannot access the application  
* Deactivated producer's products must not appear in the marketplace  
* Order history must be preserved  
* Producer status must be changed to inactive (active = false)

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| PATCH | /admin/producers/:id/deactivate | Deactivates producer |

### **FE-24 — Producer Account Deactivation**

* Add active field to the User entity  
* Create PATCH /admin/producers/:id/deactivate endpoint  
* Validate active user in authentication middleware  
* Block access for inactive producer  
* Hide inactive producer's products in the marketplace  
* Preserve historical order data

## **US-11 — Reactivate Producer**

As an administrator, I want to reactivate a producer to allow them to operate on the platform again.

**Acceptance criteria:**

* Only admin can reactivate a producer  
* Reactivated producer can access the application again  
* Products become eligible for display again

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| PATCH | /admin/producers/:id/activate | Reactivates producer |

### **FE-25 — Producer Reactivation**

* Create PATCH /admin/producers/:id/activate endpoint  
* Restore active status (active = true)  
* Re-enable producer access

# **EPIC 2 — Marketplace (Home and Navigation)**

Objective: Allow customers to discover producers and browse the marketplace in a simple, fast, and intuitive way.

## **US-12 — View Producer List**

As a customer, I want to see the available producers to discover who sells on the platform.

**Acceptance criteria:**

* List only active producers  
* Display per producer: name, photo (photoUrl), description, location, rating  
* Deactivated producers must not appear  
* List must be paginated

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /producers | Lists active producers with pagination |

### **FE-26 — List Producers Endpoint**

* Create GET /producers endpoint  
* Filter only producers with active = true  
* Implement pagination (page, limit)  
* Sort by rating desc as default

### **FE-27 — Marketplace Home Screen**

* Create home screen  
* Consume GET /producers endpoint  
* Render producer cards (photo, name, rating, location)  
* Implement infinite scroll

## **US-13 — Search Producers**

As a customer, I want to search for producers to quickly find a specific seller.

**Acceptance criteria:**

* Search by producer name  
* Search by product name  
* Search by category  
* Update results dynamically

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /producers?search=\&category= | Search with query params |

### **FE-28 — Search Backend**

* Implement search filter in GET /producers endpoint  
* Accept query params: search (name/product), category  
* Filter relevant results

### **FE-29 — Search Interface**

* Create search field on the home screen  
* Trigger API search with debounce  
* Update list dynamically

# **EPIC 3 — Producer Profile and Catalog**

Objective: Display detailed information about the producer and their available products, including photo and story, allowing customers to learn about the food's origin and build trust in the purchase.

## **US-14 — View Producer Profile**

As a customer, I want to view a producer's profile to learn about who produces the food.

**Acceptance criteria:**

* Display: name, description, story, photo (photoUrl), location, phone, rating, time on the app (memberSince)  
* List producer's available products  
* Show recent reviews

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /producers/:id | Producer profile + products + reviews |

### **FE-30 — Public Producer Profile Endpoint**

* Create/update GET /producers/:id endpoint  
* Include fields: name, description, story, photoUrl, location, phone, rating, memberSince  
* Retrieve and include associated available products  
* Retrieve and include recent reviews  
* Build consolidated response

### **FE-31 — Producer Profile Screen**

* Create profile layout  
* Display producer photo  
* Display producer story/narrative  
* Display information and reviews  
* List available products

# **EPIC 4 — Product Management**

Objective: Allow producers to register and manage the products they sell.

## **Entity Modeling — Product**

| Field | Type | Notes |
| :---- | :---- | :---- |
| id | UUID | Primary key |
| producerId | UUID FK | Reference to ProducerProfile |
| name | string | Product name |
| description | text | Detailed description |
| price | decimal | Price per unit |
| unit | enum | kg \| package |
| stock | integer | Current stock |
| active | boolean | Product visible in the marketplace |
| createdAt | timestamp |  |
| updatedAt | timestamp |  |

## **US-15 — Register Product**

As a producer, I want to register products to sell on the platform.

**Acceptance criteria:**

* Required fields: name, description, price, unit (kg or package), initial stock  
* Product must be associated with the authenticated producer

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /products | Creates product for the authenticated producer |

### **FE-32 — Product Entity**

* Create Product entity with all fields  
* Create migration  
* Create repository

### **FE-33 — Create Product Endpoint**

* Create POST /products endpoint  
* Validate producer authentication  
* Persist product linked to the producer

### **FE-34 — Product Registration Screen**

* Create registration form  
* Validate required fields  
* Integrate with API

## **US-16 — Edit Product**

As a producer, I want to edit a product to correct its information.

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| PUT | /products/:id | Updates product for the authenticated producer |

### **FE-35 — Edit Product Endpoint**

* Create PUT /products/:id endpoint  
* Validate that the producer owns the product  
* Update data in the database

### **FE-36 — Product Edit Screen**

* Load current product data  
* Allow editing  
* Save changes via API

## **US-17 — Delete Product**

As a producer, I want to delete a product to remove it from the catalog.

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| DELETE | /products/:id | Removes product for the authenticated producer |

### **FE-37 — Delete Product Endpoint**

* Create DELETE /products/:id endpoint  
* Validate that the producer owns the product  
* Remove product (soft delete recommended to preserve history)

# **EPIC 5 — Stock Control**

Objective: Allow simple stock control for producers, with a movement history.

## **Entity Modeling — StockMovement**

| Field | Type | Notes |
| :---- | :---- | :---- |
| id | UUID | Primary key |
| productId | UUID FK | Reference to Product |
| type | enum | entry \| exit |
| reason | enum | sale \| loss \| disposal \| manual\_entry |
| quantity | integer | Quantity moved |
| notes | string | Optional note |
| createdAt | timestamp |  |

## **US-18 — Register Stock Entry**

As a producer, I want to register a stock entry to update availability.

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /stock/entry | Registers stock entry for the product |

### **FE-38 — StockMovement Entity**

* Create StockMovement entity with the above fields  
* Create migration  
* Link to Product

### **FE-39 — Stock Entry Endpoint**

* Create POST /stock/entry endpoint  
* Validate producer authentication  
* Record movement and update the stock field in Product

## **US-19 — Register Stock Exit or Write-off**

As a producer, I want to register a stock exit due to sale, loss, or disposal.

**Possible reasons:**

* Sale  
* Loss  
* Disposal

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /stock/exit | Registers stock exit for the product |
| GET | /stock/:productId/movements | Movement history |

### **FE-40 — Stock Exit Endpoint**

* Create POST /stock/exit endpoint  
* Record movement with reason (sale \| loss \| disposal)  
* Update product stock  
* Create GET /stock/:productId/movements endpoint for history

# **EPIC 6 — Shopping Cart**

Objective: Allow customers to select products from a producer and prepare a purchase before placing an order.  
Important rule: The cart can only contain products from one producer at a time. If the customer tries to add a product from a different producer, the system must alert them.

## **Entity Modeling — Cart**

| Field | Type | Notes |
| :---- | :---- | :---- |
| id | UUID | Primary key |
| customerId | UUID FK | Reference to CustomerProfile |
| producerId | UUID FK | Active cart producer |
| createdAt | timestamp |  |
| updatedAt | timestamp |  |

## **Entity Modeling — CartItem**

| Field | Type | Notes |
| :---- | :---- | :---- |
| id | UUID | Primary key |
| cartId | UUID FK | Reference to Cart |
| productId | UUID FK | Reference to Product |
| quantity | integer | Quantity |
| priceSnapshot | decimal | Price at the time of addition |

## **US-20 — Automatically Create Cart**

As a customer, I want the system to automatically create a cart so I can add products before placing an order.

**Acceptance criteria:**

* Cart must be created automatically when the first item is added  
* Cart belongs to a customer  
* Cart must be cleared after order creation

### **FE-41 — Cart and CartItem Entities**

* Create Cart entity  
* Create CartItem entity  
* Create migrations and repositories

## **US-21 — Add Product to Cart**

As a customer, I want to add products to the cart to buy later.

**Acceptance criteria:**

* Product must have available stock  
* Product must belong to the same producer as the active cart  
* System must prevent adding products from different producers  
* System must automatically update quantity if the product is already in the cart

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /cart/items | Adds item to cart |

### **FE-42 — Add Item Endpoint**

* Create POST /cart/items endpoint  
* Validate customer authentication  
* Validate cart producer (1-producer rule)  
* Validate available stock  
* Create cart if it does not exist  
* Add or update item in cart

## **US-22 — Update Cart Quantity**

As a customer, I want to change the quantity of a product to adjust my purchase.

**Acceptance criteria:**

* Minimum quantity = 1  
* Validate available stock

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| PUT | /cart/items/:id | Updates item quantity |

### **FE-43 — Update Item Endpoint**

* Create PUT /cart/items/:id endpoint  
* Update quantity  
* Recalculate total amount

## **US-23 — Remove Item from Cart**

As a customer, I want to remove a product so I don't purchase it.

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| DELETE | /cart/items/:id | Removes item from cart |

### **FE-44 — Remove Item Endpoint**

* Create DELETE /cart/items/:id endpoint  
* Remove item  
* Update cart

## **US-24 — View Cart**

As a customer, I want to view the items in my cart to review my purchase.

**Acceptance criteria:**

* Display: producer, products, quantity, unit price, and total amount

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /cart | Returns active cart with items |

### **FE-45 — Cart Endpoint**

* Create GET /cart endpoint  
* Retrieve active cart for the authenticated customer  
* Return items with priceSnapshot and calculated total

# **EPIC 7 — Orders**

Objective: Allow a cart to be converted into an order and its status to be tracked, including delivery scheduling.

## **Entity Modeling — Order**

| Field | Type | Notes |
| :---- | :---- | :---- |
| id | UUID | Primary key |
| customerId | UUID FK | Reference to CustomerProfile |
| producerId | UUID FK | Reference to ProducerProfile |
| status | enum | PENDING \| CONFIRMED \| IN\_DELIVERY \| DELIVERED \| CANCELLED |
| totalPrice | decimal | Total order amount |
| deliveryAddress | jsonb | Address snapshot at the time of the order |
| scheduledFor | timestamp | Scheduled delivery date/time (optional) |
| createdAt | timestamp |  |

## **Entity Modeling — OrderItem**

| Field | Type | Notes |
| :---- | :---- | :---- |
| id | UUID | Primary key |
| orderId | UUID FK | Reference to Order |
| productId | UUID FK | Reference to Product |
| nameSnapshot | string | Product name at the time of the order |
| priceSnapshot | decimal | Price at the time of the order |
| quantity | integer | Quantity |

## **US-25 — Create Order**

As a customer, I want to complete my purchase to place an order.

**Acceptance criteria:**

* Convert cart into order  
* Validate stock for all items  
* Save name and price snapshot of products  
* Save delivery address snapshot  
* Clear cart after order creation

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /orders | Creates order from the active cart |

### **FE-46 — Order and OrderItem Entities**

* Create Order entity (including scheduledFor field)  
* Create OrderItem entity  
* Create migrations and repositories

### **FE-47 — Create Order Endpoint**

* Create POST /orders endpoint  
* Validate active cart  
* Validate stock for all items  
* Generate Order and OrderItems with snapshots  
* Save delivery address snapshot  
* Clear cart

## **US-25b — Schedule Order**

As a customer, I want to schedule an order for a future date to plan my purchases.

**Acceptance criteria:**

* Customer can specify desired delivery date and time (scheduledFor)  
* Date must be in the future  
* Scheduled order remains PENDING until confirmed by the producer  
* Producer must see the scheduled date when confirming the order

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /orders/:id/schedule | Sets the scheduled date for the order |

### **FE-47b — Schedule Order Endpoint**

* Create POST /orders/:id/schedule endpoint  
* Validate that the date is in the future  
* Save scheduledFor in the order  
* Display scheduled date on the order details screen  
* Display scheduled date to the producer upon confirmation

## **US-26 — Confirm Order by Producer**

As a producer, I want to confirm an order to start preparation.

**Acceptance criteria:**

* Order changes to CONFIRMED  
* Stock is deducted for all order items

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| PATCH | /orders/:id/confirm | Confirms order and deducts stock |

### **FE-48 — Confirm Order Endpoint**

* Create PATCH /orders/:id/confirm endpoint  
* Validate producer role  
* Change status to CONFIRMED  
* Register stock exit for each OrderItem (reason: sale)

## **US-27 — Cancel Order by Customer**

As a customer, I want to cancel an order to withdraw from the purchase.

**Acceptance criteria:**

* Only orders not yet confirmed (status PENDING) can be cancelled  
* Order changes to CANCELLED

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| PATCH | /orders/:id/cancel | Cancels order (only when status is PENDING) |

### **FE-49 — Cancel Order Endpoint**

* Create PATCH /orders/:id/cancel endpoint  
* Validate PENDING status  
* Change status to CANCELLED

## **US-28 — Update Delivery Status**

As a producer, I want to update the order status to inform about the delivery progress.

**Possible statuses:**

* PENDING  
* CONFIRMED  
* IN\_DELIVERY  
* DELIVERED  
* CANCELLED

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| PATCH | /orders/:id/status | Updates order status (producer) |

### **FE-50 — Update Status Endpoint**

* Create PATCH /orders/:id/status endpoint  
* Validate producer role  
* Validate valid status transition  
* Update status in the database

## **US-29 — Order History**

As a customer, I want to view previous orders to track my purchases.

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /orders/customer | Lists orders for the authenticated customer |
| GET | /orders/producer | Lists orders received by the authenticated producer |

### **FE-51 — History Endpoints**

* Create GET /orders/customer endpoint  
* Create GET /orders/producer endpoint  
* Include pagination and filter by status

## **US-30 — Repeat Order**

As a customer, I want to repeat a previous order to buy again with ease.

**Acceptance criteria:**

* Validate current stock for each item before repeating  
* Create new cart with the same items (if available)

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /orders/:id/repeat | Creates a new cart from a previous order |

### **FE-52 — Repeat Order Endpoint**

* Create POST /orders/:id/repeat endpoint  
* Validate current stock for each item  
* Populate active cart with available items

# **EPIC 8 — Reviews**

Objective: Allow customers to rate producers after delivery.

## **Entity Modeling — Review**

| Field | Type | Notes |
| :---- | :---- | :---- |
| id | UUID | Primary key |
| orderId | UUID FK | Reference to Order (must have DELIVERED status) |
| producerId | UUID FK | Reference to ProducerProfile |
| customerId | UUID FK | Reference to CustomerProfile |
| rating | integer | Score from 1 to 5 |
| comment | text | Optional comment |
| createdAt | timestamp |  |

## **US-31 — Rate Order**

As a customer, I want to rate a delivered order to give feedback about the producer.

**Acceptance criteria:**

* Review only available after delivery (DELIVERED status)  
* Score from 1 to 5  
* Optional comment  
* Only one review per order

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| POST | /reviews | Creates review for a delivered order |

### **FE-53 — Review Entity**

* Create Review entity  
* Create migration and repository

### **FE-54 — Rate Order Endpoint**

* Create POST /reviews endpoint  
* Validate that the order has DELIVERED status  
* Validate that no review exists for the same orderId  
* Persist review  
* Recalculate average rating field in ProducerProfile

## **US-32 — Display Producer Reviews**

As a customer, I want to see a producer's reviews to decide whether I want to buy from them.

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /producers/:id/reviews | Lists producer reviews |

### **FE-55 — Reviews Endpoint**

* Create GET /producers/:id/reviews endpoint  
* Return paginated list of reviews with rating, comment, and date

# **EPIC 9 — Logistics and Routing**

Objective: Help producers organize and optimize daily deliveries.

## **US-33 — View Today's Deliveries**

As a producer, I want to see today's orders to plan my deliveries.

**Acceptance criteria:**

* List orders with CONFIRMED or IN\_DELIVERY status for the current date  
* Include orders scheduled for today (scheduledFor = today)  
* Display the delivery address for each order

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /orders/today | Today's orders for the authenticated producer |

### **FE-56 — Today's Orders Endpoint**

* Create GET /orders/today endpoint  
* Filter by producerId and current date  
* Include orders scheduled for today (scheduledFor)

## **US-34 — View Delivery Route**

As a producer, I want to view the optimized route to make deliveries faster.

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /logistics/route | Returns optimized route for the day |

### **FE-57 — Google Maps Integration**

* Create GET /logistics/route endpoint  
* Collect addresses (with latitude/longitude) from today's orders  
* Integrate with Google Maps Directions API to generate optimized route  
* Return URL or route data for display in the app

# **EPIC 10 — Recommendations**

Objective: Suggest products to customers based on purchase history and popularity.

## **US-35 — Recommend Products**

As a customer, I want to receive product suggestions to discover new foods.

**Acceptance criteria:**

* Based on customer purchase history  
* Based on popularity (bestsellers)  
* Paginated results

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /recommendations | Lists recommended products for the authenticated customer |

### **FE-58 — Recommendation Service**

* Calculate recommendations by history (products from previously purchased producers)  
* Calculate recommendations by popularity (order count per product)  
* Combine and deduplicate results

### **FE-59 — Recommendations Endpoint**

* Create GET /recommendations endpoint  
* Return list of products with producer data

# **EPIC 11 — Producer Financial Dashboard**

Objective: Give producers visibility into their financial performance, including sales history, revenue, and volumes, as outlined in the Project Charter.

## **US-36 — View Financial Summary**

As a producer, I want to view a financial summary to track my sales performance.

**Acceptance criteria:**

* Display total delivered orders in the period  
* Display total sales amount in the period  
* Display average order value  
* Display bestselling products ranking  
* Filter by period: last 7 days, 30 days, 90 days

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /dashboard/producer/summary?period=30d | Producer financial summary |

### **FE-60 — Financial Summary Endpoint**

* Create GET /dashboard/producer/summary endpoint  
* Accept period query param (7d \| 30d \| 90d)  
* Calculate: totalOrders, totalRevenue, averageTicket  
* Calculate product ranking by quantity sold  
* Filter only orders with DELIVERED status

### **FE-61 — Financial Dashboard Screen**

* Create dashboard screen for the producer  
* Display summary cards (total sales, orders, average order value)  
* Display list of bestselling products  
* Add period selector

## **US-37 — View Sales History**

As a producer, I want to view a detailed sales history to analyze my operations.

**Acceptance criteria:**

* List delivered orders with date, amount, and items  
* Pagination  
* Filter by period

**Routes:**

| Method | Route | Description |
| :---- | :---- | :---- |
| GET | /dashboard/producer/sales?period=30d\&page=1 | Paginated sales history |

### **FE-62 — Sales History Endpoint**

* Create GET /dashboard/producer/sales endpoint  
* Filter DELIVERED orders for the authenticated producer  
* Return: id, createdAt, totalPrice, summarized items  
* Implement pagination

### **FE-63 — Sales History Screen**

* Create sales listing screen  
* Display data for each sale  
* Integrate with API