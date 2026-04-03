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
│       │   ├── config/                        # Framework configuration
│       │   │   ├── SecurityConfig.java        # Spring Security: JWT validation, role-based access
│       │   │   ├── CorsConfig.java            # CORS: allowed origins, methods, headers
│       │   │   ├── KeycloakRolesConverter.java # Extracts groups → ROLE_X authorities
│       │   │   └── OpenApiConfig.java         # Swagger UI with Keycloak OAuth2 login
│       │   │
│       │   ├── controller/                    # HTTP endpoints (REST controllers)
│       │   │   ├── AuthController.java        # POST /auth/register/customer — public registration
│       │   │   ├── UserController.java        # GET /users/me — authenticated user profile
│       │   │   ├── CustomerController.java    # GET /customers/me — customer profile with addresses
│       │   │   ├── AdminUserController.java   # POST /admin/users — create user (admin)
│       │   │   ├── RoleAccessController.java  # Role-based test endpoints (admin, farmer, customer)
│       │   │   ├── request/                   # Request DTOs (inbound)
│       │   │   │   ├── UserRequest.java       # name, email, phone, type — with validation
│       │   │   │   └── CustomerRegistrationRequest.java  # Full registration DTO
│       │   │   └── response/                  # Response DTOs (outbound)
│       │   │       ├── UserResponse.java      # id, name, email, phone, type, active, timestamps
│       │   │       ├── CustomerResponse.java  # Customer profile with addresses
│       │   │       └── ErrorResponse.java     # timestamp, status, error, path
│       │   │
│       │   ├── domain/                        # JPA entities and enums
│       │   │   ├── User.java                  # Maps to `users` table
│       │   │   ├── Customer.java              # Maps to `customers` table (1:1 with User)
│       │   │   ├── Address.java               # Maps to `addresses` table
│       │   │   └── enums/
│       │   │       └── TypeUser.java          # FARMER | CUSTOMER | ADMIN
│       │   │
│       │   ├── service/                       # Business logic
│       │   │   ├── UserService.java           # User lookup, authentication, JWT claim extraction
│       │   │   ├── CustomerService.java       # Customer profile operations
│       │   │   ├── CustomerRegistrationService.java  # Registration orchestration (Keycloak + DB)
│       │   │   ├── IdentityProviderService.java      # Interface for auth provider
│       │   │   └── KeycloakIdentityProviderService.java  # Keycloak Admin REST API implementation
│       │   │
│       │   ├── repository/                    # Data access (Spring Data JPA)
│       │   │   ├── UserRepository.java        # CRUD + findByEmail, findByAuthSub, search
│       │   │   ├── CustomerRepository.java    # Customer-specific queries
│       │   │   └── AddressRepository.java     # Address queries
│       │   │
│       │   ├── mapper/                        # Entity <-> DTO conversion
│       │   │   ├── UserMapper.java            # toEntity(request), toResponse(entity)
│       │   │   ├── CustomerMapper.java        # toEntity(user, fiscalNumber), toResponse(user)
│       │   │   └── AddressMapper.java         # toEntity(request, user), toResponse(address)
│       │   │
│       │   └── exception/                     # Error handling
│       │       ├── BusinessException.java     # 400 — business rule violations
│       │       ├── NotFoundException.java     # 404 — resource not found
│       │       ├── UnauthorizedException.java # 401 — authentication failures
│       │       └── GlobalExceptionHandler.java# @RestControllerAdvice — catches all exceptions
│       │
│       └── resources/
│           └── application.yml                # Spring Boot + Keycloak configuration
│
├── data/
│   ├── schema.sql                             # Database schema (auto-applied by Docker)
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
│   ├── database.md                            # Full database documentation (21 tables, ER diagram)
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
| `config` | Framework configuration (security, CORS, Keycloak converter, OpenAPI) |
| `controller` | REST endpoints — receives requests, returns responses |
| `controller.request` | Inbound DTOs with Jakarta Validation annotations |
| `controller.response` | Outbound DTOs — serialized to JSON |
| `domain` | JPA entities — maps to database tables |
| `domain.enums` | Enums used by entities (TypeUser, etc.) |
| `service` | Business logic and orchestration |
| `repository` | Spring Data JPA interfaces |
| `mapper` | Entity <-> DTO converters |
| `exception` | Custom exceptions and global handler |
| `exception.response` | Error response DTOs |

---

## Future Structure

As new domains are implemented, the package structure will grow to accommodate new entities, services, and controllers. The recommended approach is to keep the current flat structure for shared components and introduce domain-specific packages only when complexity warrants it:

```
controller/
├── UserController.java
├── ProductController.java       # Epic 4
├── CartController.java          # Epic 6
├── OrderController.java         # Epic 7
├── ReviewController.java        # Epic 8
├── LogisticsController.java     # Epic 9
└── DashboardController.java     # Epic 11
```
