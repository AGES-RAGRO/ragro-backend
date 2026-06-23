# RAGRO Backend Architecture — Project Structure

## Fully Annotated Overview

```
ragro-backend/
│
├── src/
│   └── main/
│       ├── java/br/com/ragro/
│       │   │
│       │   ├── RagroApplication.java          # Spring Boot entry point
│       │   │
│       │   ├── config/                        # Framework configuration (20 files)
│       │   │   ├── SecurityConfig.java        # Spring Security: JWT validation, public chain, role-based access
│       │   │   ├── CorsConfig.java            # CORS: allowed origins, methods, headers
│       │   │   ├── KeycloakRolesConverter.java # Extracts groups → ROLE_X authorities
│       │   │   ├── OpenApiConfig.java         # Swagger UI with Keycloak OAuth2 login
│       │   │   ├── ActiveUserFilter.java      # Rejects requests from deactivated users
│       │   │   ├── AsyncConfig.java           # @Async executor (post-commit notifications)
│       │   │   ├── CacheConfig.java           # Cache manager (recommendations, etc.)
│       │   │   ├── FlywayConfig.java          # Flyway migration bootstrap
│       │   │   ├── FirebaseConfig.java        # FCM init (FIREBASE_SERVICE_ACCOUNT_JSON)
│       │   │   ├── FirebaseEnabledCondition.java # Conditional FCM bean activation
│       │   │   ├── MinioConfig.java           # MinIO client (media storage)
│       │   │   ├── MinioProperties.java       # MINIO/STORAGE_* properties
│       │   │   ├── GoogleRoutesClientConfig.java # Google Routes API HTTP client
│       │   │   ├── NvidiaChatConfig.java      # Spring AI → NVIDIA LLM chat client (reranker)
│       │   │   ├── RateLimitConfig.java       # Rate limit bean wiring
│       │   │   ├── RateLimitFilter.java       # Per-request rate limiting
│       │   │   ├── RateLimitProperties.java   # Rate limit thresholds
│       │   │   ├── WebSocketConfig.java       # STOMP endpoints for real-time tracking
│       │   │   ├── TrackingChannelInterceptor.java # Auth for STOMP tracking subscriptions
│       │   │   └── TrackingProperties.java    # GPS tracking config
│       │   │
│       │   ├── controller/                    # HTTP endpoints (20 controllers)
│       │   │   ├── AuthController.java        # /auth — registration, config, forgot/reset, session
│       │   │   ├── AdminController.java       # /admin — user management, dashboard (ROLE_ADMIN)
│       │   │   ├── CustomerController.java    # /customers — customer profile with addresses (ROLE_CUSTOMER)
│       │   │   ├── ProducerController.java    # /producers — producer dashboard (ROLE_FARMER)
│       │   │   ├── ProductController.java     # /products — product catalog
│       │   │   ├── CartController.java        # /cart — shopping cart
│       │   │   ├── OrderController.java       # /orders — order lifecycle, delivery confirmation codes
│       │   │   ├── OrderTrackingController.java # Order tracking (REST)
│       │   │   ├── TrackingWsController.java  # WebSocket/STOMP real-time GPS tracking
│       │   │   ├── RouteController.java       # /routes — route optimization (Google Routes API)
│       │   │   ├── Co2Controller.java         # CO2 savings reporting
│       │   │   ├── RecommendationController.java # LLM recommendations (Spring AI → NVIDIA)
│       │   │   ├── ReviewController.java      # Product/producer reviews
│       │   │   ├── SearchController.java      # Search
│       │   │   ├── StockController.java       # Stock / inventory
│       │   │   ├── MediaController.java       # GET /media/** — media served from MinIO
│       │   │   ├── FavoriteProducerController.java # Customer favorite producers
│       │   │   ├── NotificationController.java # Notification base endpoints
│       │   │   ├── CustomerNotificationController.java # /customers/me/notifications
│       │   │   ├── ProducerNotificationController.java # Producer notifications
│       │   │   ├── request/                   # Request DTOs (inbound)
│       │   │   │   ├── UserRequest.java       # name, email, phone, type — with validation
│       │   │   │   └── CustomerRegistrationRequest.java  # Full registration DTO
│       │   │   └── response/                  # Response DTOs (outbound)
│       │   │       ├── UserResponse.java      # id, name, email, phone, type, active, timestamps
│       │   │       ├── CustomerResponse.java  # Customer profile with addresses
│       │   │       ├── AuthConfigResponse.java # Keycloak token URL, client ID, realm
│       │   │       ├── SessionResponse.java   # Authenticated user session data
│       │   │       └── ErrorResponse.java     # timestamp, status, error, path
│       │   │
│       │   ├── domain/                        # JPA entities, enums, events, LLM models, specs (28 entities)
│       │   │   ├── User.java                  # Maps to `users` table
│       │   │   ├── Customer.java              # Maps to `customers` table (1:1 with User)
│       │   │   ├── Address.java               # Maps to `addresses` table
│       │   │   ├── Producer.java / ProducerProfile.java # Producer + profile
│       │   │   ├── Product.java / ProductPhoto.java / ProductCategory.java # Catalog
│       │   │   ├── Cart.java / CartItem.java  # Shopping cart
│       │   │   ├── Order.java / OrderItem.java / OrderStatusHistory.java / AddressSnapshot.java # Orders
│       │   │   ├── PaymentMethod.java / Review.java / FavoriteProducer.java / FavoriteProducerId.java
│       │   │   ├── StockMovement.java         # Stock ledger
│       │   │   ├── DeliveryRoute.java / RouteStop.java / RoutePosition.java # Delivery routes + GPS
│       │   │   ├── Co2Emission.java / Co2Saving.java # CO2 tracking
│       │   │   ├── FcmToken.java / Notification.java # FCM push + notifications
│       │   │   ├── VehiclePreference.java / FarmerAvailability.java
│       │   │   ├── enums/                     # 14 enums
│       │   │   │   ├── TypeUser.java          # FARMER | CUSTOMER | ADMIN
│       │   │   │   ├── OrderStatus.java / PaymentStatus.java / ProductCategory.java
│       │   │   │   ├── DeliveryRouteStatus.java / RouteStopStatus.java / GeocodeStatus.java
│       │   │   │   ├── StockMovementType.java / StockMovementReason.java
│       │   │   │   ├── NotificationType.java / NotificationReferenceType.java
│       │   │   │   └── FuelType.java / VehicleType.java / RecommendationReason.java
│       │   │   ├── event/                     # Domain events
│       │   │   │   └── OrderStatusChangedEvent.java
│       │   │   ├── llm/                       # LLM reranker models
│       │   │   │   ├── Candidate.java / CustomerFeatures.java
│       │   │   │   └── RankedItem.java / RankedRecommendation.java
│       │   │   └── specification/             # JPA Specifications
│       │   │       ├── ProducerSpecification.java
│       │   │       └── StockMovementSpecification.java
│       │   │
│       │   ├── service/                       # Business logic
│       │   │   ├── UserService.java           # User lookup, authentication, JWT claim extraction
│       │   │   ├── CustomerService.java / CustomerRegistrationService.java # Customer + registration
│       │   │   ├── ProducerService.java / ProducerRegistrationService.java # Producer + registration
│       │   │   ├── ProductService.java / CartService.java / OrderService.java # Catalog, cart, orders
│       │   │   ├── StockService.java / StockMovementService.java / SearchService.java
│       │   │   ├── ReviewService.java / FavoriteProducerService.java / DashboardService.java
│       │   │   ├── DeliveryRouteService.java / GoogleRoutesService.java / GoogleMapsService.java # Routes + geocoding
│       │   │   ├── TrackingService.java / AddressGeocoder.java / PolylineUtil.java # Tracking + geo utils
│       │   │   ├── Co2Service.java / RecommendationService.java / RecommendationWarmupService.java
│       │   │   ├── NotificationService.java / FcmService.java # Notifications + FCM push
│       │   │   ├── MediaResource.java / MinioStorageService.java # Media (MinIO)
│       │   │   ├── api/                       # Ports (interfaces)
│       │   │   │   ├── IdentityProviderService.java # Interface for auth provider
│       │   │   │   ├── LlmRerankerPort.java   # Port for LLM reranking
│       │   │   │   └── PositionStore.java     # Port for GPS position storage
│       │   │   └── impl/                      # Adapters (implementations)
│       │   │       ├── KeycloakIdentityProviderService.java # Keycloak Admin REST API implementation
│       │   │       ├── SpringAiRerankerAdapter.java / DisabledRerankerAdapter.java / RerankOutput.java
│       │   │       └── InMemoryPositionStore.java
│       │   │
│       │   ├── repository/                    # Data access (Spring Data JPA)
│       │   │   ├── UserRepository.java        # CRUD + findByEmail, findByAuthSub, search
│       │   │   ├── CustomerRepository.java    # Customer-specific queries
│       │   │   └── AddressRepository.java     # Address queries (plus repositories for every domain entity)
│       │   │
│       │   ├── mapper/                        # Entity <-> DTO conversion
│       │   │   ├── UserMapper.java            # toEntity(request), toResponse(entity)
│       │   │   ├── CustomerMapper.java        # toEntity(user, fiscalNumber), toResponse(user)
│       │   │   └── AddressMapper.java         # toEntity(request, user), toResponse(address)
│       │   │
│       │   ├── event/                         # Application events
│       │   │   └── OrderPushNotificationEvent.java
│       │   │
│       │   ├── listener/                      # Event listeners
│       │   │   └── PushNotificationListener.java # Sends FCM push on order events
│       │   │
│       │   ├── validation/                    # Custom Jakarta Validation constraints
│       │   │   ├── ValidCpf.java / CpfValidator.java
│       │   │   ├── ValidFiscalNumber.java / FiscalNumberValidator.java
│       │   │   └── CnpjValidator.java
│       │   │
│       │   └── exception/                     # Error handling
│       │       ├── BusinessException.java     # 400 — business rule violations
│       │       ├── NotFoundException.java     # 404 — resource not found
│       │       ├── UnauthorizedException.java # 401 — authentication failures
│       │       ├── ForbiddenException.java    # 403 — authorization failures
│       │       ├── ConflictException.java     # 409 — conflicts (e.g. duplicate)
│       │       ├── InternalServerException.java # 500 — internal errors
│       │       ├── GoogleApiException.java    # Google Routes/Maps API failures
│       │       ├── LlmInvalidOutputException.java # Invalid LLM reranker output
│       │       └── GlobalExceptionHandler.java# @RestControllerAdvice — catches all exceptions
│       │
│       └── resources/
│           ├── application.yml                # Spring Boot + Keycloak configuration
│           ├── application-local.yml          # Local profile overrides
│           └── db/migration/                  # Flyway migrations (V1..V24, V15 skipped; latest V24__route_positions)
│
├── data/
│   └── 00-create-keycloak-db.sh               # Init script: creates keycloak database
│
├── keycloak/
│   └── ragro-realm.json                       # Pre-configured Keycloak realm (groups, client, users)
│
├── docs/                                      # Project documentation
│   ├── api/
│   │   ├── overview.md                        # Base URL, auth, error format, CORS
│   │   └── endpoints.md                       # Endpoint reference (implemented + planned)
│   ├── architecture/
│   │   ├── 01-overview.md                     # Tech stack, architecture pattern, request flow
│   │   ├── 02-project-structure.md            # This file — annotated folder structure
│   │   ├── 03-layers.md                       # Layer responsibilities and rules
│   │   ├── 04-security.md                     # JWT, Keycloak, role-based access
│   │   └── 05-error-handling.md               # Exception hierarchy and error responses
│   ├── conventions.md                         # Naming, coding, and workflow conventions
│   ├── database.md                            # Full database documentation (26 tables, ER diagram)
│   └── backlog_ragro.md                       # Product backlog with all epics and user stories
│
├── docker-compose.yml                         # Dev stack: PostgreSQL + Keycloak + Spring Boot
├── docker-compose.test.yml                    # Test stack with fresh database
├── Dockerfile                                 # Multi-stage build for the API
├── pom.xml                                    # Maven dependencies and build config
├── checkstyle.xml                             # Code style rules
└── README.md                                  # Quick start and project overview
```

---

## Package Naming Convention

All packages follow the base namespace `br.com.ragro`:

| Package | Responsibility |
|---------|---------------|
| `config` | Framework configuration (security, CORS, Keycloak, OpenAPI, Flyway, MinIO, Firebase/FCM, NVIDIA chat, rate limiting, WebSocket/tracking) |
| `controller` | REST endpoints — receives requests, returns responses |
| `controller.request` | Inbound DTOs with Jakarta Validation annotations |
| `controller.response` | Outbound DTOs — serialized to JSON (incl. `ErrorResponse`) |
| `domain` | JPA entities — maps to database tables |
| `domain.enums` | Enums used by entities (TypeUser, OrderStatus, etc.) |
| `domain.event` | Domain events (e.g. `OrderStatusChangedEvent`) |
| `domain.llm` | LLM reranker models (candidates, features, ranked results) |
| `domain.specification` | JPA Specifications for dynamic queries |
| `service` | Business logic and orchestration |
| `service.api` | Ports — interfaces for external integrations (identity provider, LLM reranker, position store) |
| `service.impl` | Adapters — implementations of the ports (Keycloak, Spring AI reranker, in-memory position store) |
| `repository` | Spring Data JPA interfaces |
| `mapper` | Entity <-> DTO converters |
| `event` | Application events (e.g. `OrderPushNotificationEvent`) |
| `listener` | Event listeners (e.g. `PushNotificationListener`) |
| `validation` | Custom Jakarta Validation constraints (CPF, CNPJ, fiscal number) |
| `exception` | Custom exceptions and global handler |

> The database schema is owned by Flyway. See `src/main/resources/db/migration/` (V1..V24, V15 skipped; latest `V24__route_positions.sql`) for the authoritative schema, and [`docs/database.md`](../database.md) for the full ER documentation.
