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
│       │   │   └── CognitoGroupsAuthoritiesConverter.java
│       │   │                                  # Extracts cognito:groups → ROLE_X authorities
│       │   │
│       │   ├── controller/                    # HTTP endpoints (REST controllers)
│       │   │   ├── UserController.java        # GET /users/me — authenticated user profile
│       │   │   ├── AdminUserController.java   # POST /admin/users — create user (admin)
│       │   │   ├── RoleAccessController.java  # Role-based dashboards (admin, farmer, customer)
│       │   │   ├── request/                   # Request DTOs (inbound)
│       │   │   │   └── UserRequest.java       # name, email, phone — with validation
│       │   │   └── response/                  # Response DTOs (outbound)
│       │   │       ├── UserResponse.java      # id, name, email, phone, type, active, timestamps
│       │   │       └── ErrorResponse.java     # timestamp, status, error, path — used by GlobalExceptionHandler
│       │   │
│       │   ├── domain/                        # JPA entities and enums
│       │   │   ├── User.java                  # Maps to `users` table
│       │   │   └── enums/
│       │   │       └── TypeUser.java          # FARMER | CUSTOMER | ADMIN
│       │   │
│       │   ├── service/                       # Business logic
│       │   │   └── UserService.java           # User creation, authentication, JWT claim extraction
│       │   │
│       │   ├── repository/                    # Data access (Spring Data JPA)
│       │   │   └── UserRepository.java        # CRUD + findByEmail, findByCognitoSub, search
│       │   │
│       │   ├── mapper/                        # Entity ↔ DTO conversion
│       │   │   └── UserMapper.java            # toEntity(request), toResponse(entity)
│       │   │
│       │   └── exception/                     # Error handling
│       │       ├── BusinessException.java     # 400 — business rule violations
│       │       ├── NotFoundException.java     # 404 — resource not found
│       │       ├── UnauthorizedException.java # 401 — authentication failures
│       │       └── GlobalExceptionHandler.java# @RestControllerAdvice — catches all exceptions
│       │
│       └── resources/
│           ├── application.yml                # Spring Boot configuration
│           └── authz.pem                      # Public key for JWT validation
│
├── data/
│   └── schema.sql                             # Database schema (auto-applied by Docker)
│
├── docs/                                      # Project documentation
│   ├── api/
│   │   ├── overview.md                        # Base URL, auth, error format, CORS
│   │   └── endpoints.md                       # Endpoint reference (implemented + planned)
│   ├── architecture/
│   │   ├── 01-overview.md                     # Tech stack, architecture pattern, request flow
│   │   ├── 02-project-structure.md            # This file — annotated folder structure
│   │   ├── 03-layers.md                       # Layer responsibilities and rules
│   │   ├── 04-security.md                     # JWT, Cognito, role-based access
│   │   └── 05-error-handling.md               # Exception hierarchy and error responses
│   ├── conventions.md                         # Naming, coding, and workflow conventions
│   ├── database.md                            # Full database documentation (21 tables, ER diagram)
│   └── backlog_ragro.md                       # Product backlog with all epics and user stories
│
├── docker-compose.yml                         # Dev stack: PostgreSQL + Spring Boot
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
| `config` | Framework configuration (security, CORS, converters) |
| `controller` | REST endpoints — receives requests, returns responses |
| `controller.request` | Inbound DTOs with Jakarta Validation annotations |
| `controller.response` | Outbound DTOs — serialized to JSON |
| `domain` | JPA entities — maps to database tables |
| `domain.enums` | Enums used by entities (TypeUser, etc.) |
| `service` | Business logic and orchestration |
| `repository` | Spring Data JPA interfaces |
| `mapper` | Entity ↔ DTO converters |
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
