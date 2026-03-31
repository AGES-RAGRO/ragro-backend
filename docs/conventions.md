# RAGRO Backend — Project Conventions

This document centralizes all naming, structure, and best practice conventions adopted in the RAGRO backend. Following these conventions is mandatory to maintain consistency and traceability.

---

## 1. Terminology Glossary

The product backlog and the database/code use different terms for the same concepts. This table maps them:

| Backlog (User-facing) | Database / Code | Where used |
|------------------------|-----------------|------------|
| Consumer | Customer | `users.type = 'customer'`, `customers` table, `TypeUser.CUSTOMER`, `ROLE_CUSTOMER` |
| Producer | Farmer | `users.type = 'farmer'`, `farmers` table, `TypeUser.FARMER`, `ROLE_FARMER` |
| Admin | Admin | `users.type = 'admin'`, `TypeUser.ADMIN`, `ROLE_ADMIN` |
| ProducerProfile | Farmer | `farmers` table — 1:1 with `users` |
| ConsumerProfile | Customer | `customers` table — 1:1 with `users` |

**Rule**: In user-facing text (API docs, error messages, mobile app), use backlog terms (consumer, producer). In code (entities, database, variables), use the database terms (customer, farmer).

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
- Throw custom exceptions (`BusinessException`, `NotFoundException`, `UnauthorizedException`)
- Never depend on HTTP-specific classes
- Use mappers for entity ↔ DTO conversion

### Repository

- Extend `JpaRepository<Entity, UUID>`
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

- The schema is defined in `data/schema.sql`
- Hibernate DDL mode is `validate` — it never creates or alters tables
- All schema changes must be made in `schema.sql` and applied manually or via Docker restart

### Entity Mapping

- `@Table(name = "table_name")` — always explicit
- `@Column(name = "column_name")` — always explicit for non-trivial mappings
- `@GeneratedValue(strategy = GenerationType.UUID)` for primary keys
- `@Enumerated(EnumType.STRING)` for all enums
- Timestamp fields use `OffsetDateTime` mapped to `timestamptz`

### Naming

| Java | Database |
|------|----------|
| `createdAt` | `created_at` |
| `cognitoSub` | `cognito_sub` |
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

- Throw `BusinessException` for business rule violations → `400`
- Throw `NotFoundException` for missing resources → `404`
- Throw `UnauthorizedException` for auth failures → `401`
- All exceptions are caught by `GlobalExceptionHandler`
- Error responses follow the `ErrorResponse` format (timestamp, status, error, path)

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
5. Create/update entity              → align with schema.sql
6. Create/update repository          → Spring Data JPA interface
7. Create/update mapper              → entity ↔ DTO conversion
8. Create/update service             → business logic
9. Create/update controller          → REST endpoint with validation
10. Write tests                      → unit and/or integration
```

Never skip steps. Never implement based on assumptions.
