# RAGRO Backend Architecture — Overview

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 21 | Programming language (LTS) |
| **Spring Boot** | 3.3 | Application framework |
| **Spring Security** | 6.x | Authentication and authorization |
| **Spring Data JPA** | 3.x | Data access and ORM |
| **PostgreSQL** | 16 | Relational database |
| **AWS Cognito** | — | Identity provider (JWT) |
| **Docker** | 24+ | Containerization |
| **Maven** | 3.9+ | Build and dependency management |

---

## Architectural Pattern

The project follows a **layered architecture** aligned with Spring Boot conventions:

```
┌─────────────────────────────────────────────────────────┐
│                    CONTROLLER LAYER                       │
│  Receives HTTP requests, validates input, delegates       │
│  to the service layer. Returns DTOs, never entities.      │
├─────────────────────────────────────────────────────────┤
│                     SERVICE LAYER                         │
│  Contains business logic. Orchestrates repositories       │
│  and external integrations. Manages transactions.         │
├─────────────────────────────────────────────────────────┤
│                   REPOSITORY LAYER                        │
│  Data access via Spring Data JPA. Provides CRUD           │
│  operations and custom queries on entities.                │
├─────────────────────────────────────────────────────────┤
│                     DOMAIN LAYER                          │
│  JPA entities mapped to database tables. Represent         │
│  the core business model.                                  │
└─────────────────────────────────────────────────────────┘
```

---

## Request Flow

```
Client (Mobile App)
  │
  │  HTTP Request + Bearer JWT
  ▼
SecurityConfig (Spring Security Filter Chain)
  │  Validates JWT signature via Cognito JWKS
  │  Extracts cognito:groups → ROLE_ADMIN / ROLE_FARMER / ROLE_CUSTOMER
  ▼
Controller
  │  @RequestMapping — receives request
  │  Validates @RequestBody with Jakarta Validation
  │  Extracts @AuthenticationPrincipal Jwt
  ▼
Service
  │  Business logic and orchestration
  │  @Transactional when needed
  ▼
Repository (Spring Data JPA)
  │  Database queries
  ▼
PostgreSQL
```

**Response travels back up the same path**: `Repository → Service → Controller → JSON response`.

---

## Key Design Decisions

1. **Stateless sessions** — No server-side session storage. Authentication is entirely JWT-based via AWS Cognito.

2. **Cognito as identity provider** — User creation in Cognito is separate from database persistence. The `cognito_sub` field bridges the two systems.

3. **DTOs at the boundary** — Controllers receive `*Request` objects and return `*Response` objects. JPA entities never leak to the API surface.

4. **Mapper classes** — Dedicated mapper classes handle conversion between entities and DTOs, keeping controllers and services clean.

5. **Schema-first database** — The schema is defined in `data/schema.sql` and applied on first Docker startup. Hibernate is set to `validate` mode — it checks the schema but never modifies it.

6. **Global exception handling** — A `@RestControllerAdvice` catches all business exceptions and returns standardized error responses.
