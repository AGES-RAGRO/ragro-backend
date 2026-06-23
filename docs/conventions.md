# RAGRO Backend — Project Conventions

This document centralizes all naming, structure, and best practice conventions adopted in the RAGRO backend. Following these conventions is mandatory to maintain consistency and traceability.

---

## 1. Terminology Glossary

The product backlog and the database/code use different terms for the same concepts. This table maps them:

| Backlog (User-facing) | Database / Code | Where used |
|------------------------|-----------------|------------|
| Customer | Customer | `users.type = 'CUSTOMER'`, `customers` table, `TypeUser.CUSTOMER`, `ROLE_CUSTOMER` |
| Producer | Farmer | `users.type = 'FARMER'`, `farmers` table (mapped by the `Producer` entity), `TypeUser.FARMER`, `ROLE_FARMER` |
| Admin | Admin | `users.type = 'ADMIN'`, `TypeUser.ADMIN`, `ROLE_ADMIN` |
| Producer (`farmers`) | Farmer | `Producer` entity → `farmers` table — 1:1 with `users` |
| ProducerProfile | ProducerProfile | `ProducerProfile` entity → `producer_profiles` table |
| CustomerProfile | Customer | `customers` table — 1:1 with `users` |

> `users.type` is an `@Enumerated(EnumType.STRING)` `TypeUser` on a `varchar(20)` column, so the JPA layer persists the enum **name** in uppercase (`FARMER` / `CUSTOMER` / `ADMIN`). The lowercase literals (`'farmer'`, …) only survive in legacy DB-trigger comparisons from `V1__initial_schema.sql`.

**Rule**: In user-facing text (API docs, error messages, mobile app) and in code (entities, database, variables), use the terms above consistently. For producers, the user-facing term is "producer" while the code/database term is "farmer".

---

## 2. Naming Conventions

### Packages and Classes

| Type | Package | Class Pattern | Example |
|------|---------|---------------|---------|
| Entity | `domain` | `PascalCase` (singular) | `User`, `Product`, `Order` |
| Enum | `domain.enums` | `PascalCase` | `TypeUser`, `OrderStatus` |
| Controller | `controller` | `NameController` | `UserController`, `ProductController` |
| Service | `service` | `NameService` | `UserService`, `OrderService` |
| Repository | `repository` | `NameRepository` | `UserRepository`, `ProductRepository` |
| Mapper | `mapper` | `NameMapper` | `UserMapper`, `ProductMapper` |
| Request DTO | `controller.request` | `NameRequest` | `UserRequest`, `ProductRequest` |
| Response DTO | `controller.response` | `NameResponse` | `UserResponse`, `ProductResponse` |
| Exception | `exception` | `NameException` | `BusinessException`, `NotFoundException` |

### General Rules

- **Packages**: always `lowercase` (e.g., `controller.request`)
- **Classes**: always `PascalCase`
- **Methods and variables**: always `camelCase`
- **Constants**: `SCREAMING_SNAKE_CASE`
- **Database columns**: `snake_case` (mapped via `@Column(name = "...")`)
- **API paths**: `kebab-case` (e.g., `/cart/items`, `/orders/today`)

---

## 3. Layer Rules

### Controller

- Receive `@Valid @RequestBody` for input validation
- Extract JWT via `@AuthenticationPrincipal Jwt jwt`
- Delegate all logic to the service layer
- Return `ResponseEntity<T>` with appropriate HTTP status
- Never call repositories directly
- Never return JPA entities — always use response DTOs

### Service

- Contain all business logic
- Use `@Transactional` for operations that modify data
- Throw custom exceptions from the `exception` package (`BusinessException`, `NotFoundException`, `ConflictException`, `ForbiddenException`, … — see [Error Handling](#6-error-handling))
- Never depend on HTTP-specific classes
- Use mappers for entity ↔ DTO conversion

### Repository

- Extend `JpaRepository<Entity, ID>` — the ID is typically `UUID`, but lookup/reference entities use other types (e.g. `ProductCategoryRepository extends JpaRepository<ProductCategory, Integer>`)
- Use Spring Data query method naming for simple queries
- Use `@Query` for complex queries
- Return `Optional<T>` for single-entity lookups
- Never contain business logic

### Mapper

- Static utility methods: `toEntity()`, `toResponse()`
- Handle null values gracefully
- One mapper per domain entity

---

## 4. Database Conventions

### Schema Management

- The schema is versioned via Flyway migrations in `src/main/resources/db/migration`
- Hibernate DDL mode is `validate` — it never creates or alters tables
- All schema changes must be done through a new migration file (`V{n}__description.sql`)

### Entity Mapping

- `@Table(name = "table_name")` — always explicit
- `@Column(name = "column_name")` — always explicit for non-trivial mappings
- `@GeneratedValue(strategy = GenerationType.UUID)` for primary keys (typical case; lookup/reference entities such as `ProductCategory` use `GenerationType.IDENTITY` with an `Integer` id)
- `@Enumerated(EnumType.STRING)` for all enums
- Timestamp fields use `OffsetDateTime` mapped to `timestamptz`

### Naming

| Java | Database |
|------|----------|
| `createdAt` | `created_at` |
| `authSub` | `auth_sub` |
| `TypeUser.FARMER` | `'farmer'` |

---

## 5. Validation

- Use Jakarta Validation annotations on request DTOs:
  - `@NotBlank` for required strings
  - `@Email` for email fields
  - `@NotNull` for required non-string fields
  - `@Size`, `@Min`, `@Max` for constraints
- Validate at the controller boundary — never deeper

---

## 6. Error Handling

- All custom exceptions live in the `exception` package and are caught by `GlobalExceptionHandler`
- Error responses follow the `ErrorResponse` format (timestamp, status, error, path)

| Exception | HTTP status | When to throw |
|-----------|-------------|---------------|
| `BusinessException` | `400` | Business rule violations |
| `NotFoundException` | `404` | Missing resources |
| `UnauthorizedException` | `401` | Auth failures |
| `ForbiddenException` | `403` | Authenticated but not allowed |
| `ConflictException` | `409` | State/uniqueness conflicts |
| `InternalServerException` | `500` | Unrecoverable server errors |
| `LlmInvalidOutputException` | `400` | Invalid LLM output (subclass of `BusinessException`) |
| `GoogleApiException` | `503` / `422` / `500` | Google integration failures, by `Kind` (QUOTA/TRANSIENT → `503` + `Retry-After`; INVALID_INPUT → `422`; else `500`) |

The handler also maps Spring's `AccessDeniedException` → `403`, optimistic-lock / data-integrity violations → `409`, and `MaxUploadSizeExceededException` → `400`.

---

## 7. Git Conventions

### Branch Naming

```
feature/US-XX-short-description
bugfix/US-XX-short-description
hotfix/short-description
```

### Commit Messages

```
feat(US-XX): short description of the change
fix(US-XX): short description of the fix
refactor: short description
docs: short description
test: short description
```

---

## 8. Workflow — Mandatory Order

```
1. Read docs/backlog_ragro.md        → identify the user story and acceptance criteria
2. Read docs/database.md             → confirm table structure and relationships
3. Read docs/architecture/           → confirm package structure and patterns
4. Read docs/conventions.md          → follow naming and layer rules
5. Create/update entity              → align with docs/database.md and the latest Flyway migration
6. Create/update repository          → Spring Data JPA interface
7. Create/update mapper              → entity ↔ DTO conversion
8. Create/update service             → business logic
9. Create/update controller          → REST endpoint with validation
10. Write tests                      → unit and/or integration
```

Never skip steps. Never implement based on assumptions.
