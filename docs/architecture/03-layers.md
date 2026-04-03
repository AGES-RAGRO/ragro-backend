# RAGRO Backend Architecture — Layers

## Layer Responsibilities

### Controller Layer

The entry point for HTTP requests. Controllers are responsible for:

- Receiving and validating request bodies (`@Valid @RequestBody`)
- Extracting the authenticated user from the JWT (`@AuthenticationPrincipal Jwt`)
- Delegating business logic to the service layer
- Returning response DTOs — **never JPA entities**

```java
@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyUser(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getMyUser(jwt));
    }
}
```

**Rules:**
- Controllers must not contain business logic
- Controllers must not call repositories directly
- One controller per domain/resource

---

### Service Layer

Contains all business logic and orchestration:

- Enforcing business rules (e.g., "email must be unique")
- Coordinating between multiple repositories
- Extracting and validating JWT claims
- Managing transactions (`@Transactional`)

```java
@Service
public class UserService {

    public UserResponse addUser(Jwt jwt, UserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered");
        }
        // ... create user
    }
}
```

**Rules:**
- Services must not depend on HTTP-specific objects (HttpServletRequest, ResponseEntity)
- Services return DTOs or domain objects — never HTTP responses
- Each service focuses on a single domain

---

### Repository Layer

Data access via Spring Data JPA:

- Extends `JpaRepository<Entity, UUID>` for CRUD operations
- Defines custom query methods using Spring Data naming conventions
- Uses `@Query` for complex queries

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByAuthSub(String authSub);
    boolean existsByEmail(String email);
}
```

**Rules:**
- Repositories are interfaces — Spring generates the implementation
- No business logic in repositories
- Return `Optional<T>` for single-entity lookups

---

### Domain Layer

JPA entities mapped to PostgreSQL tables:

- Annotated with `@Entity`, `@Table`, `@Column`
- Use `UUID` as primary key type
- Track creation and update timestamps

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    // ...
}
```

**Rules:**
- Entities represent the database schema — keep them aligned with `data/schema.sql`
- Hibernate DDL is set to `validate` — entities must match the existing schema exactly
- Enums use `@Enumerated(EnumType.STRING)` for readability

---

### Mapper Layer

Dedicated classes for converting between entities and DTOs:

```java
public class UserMapper {

    public static User toEntity(UserRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setActive(true);
        return user;
    }

    public static UserResponse toResponse(User entity) {
        UserResponse response = new UserResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        // ...
        return response;
    }
}
```

**Rules:**
- Mappers are utility classes with static methods
- One mapper per domain entity
- Mappers handle null checks gracefully

---

## Layer Dependency Rules

```
┌──────────────────────────────────────────────────────┐
│  controller/                                          │
│    MAY import:     service/, controller.request/,     │
│                    controller.response/                │
│    MAY NOT import: repository/, domain/ (directly)    │
├──────────────────────────────────────────────────────┤
│  service/                                             │
│    MAY import:     repository/, domain/, mapper/,     │
│                    exception/                          │
│    MAY NOT import: controller/                        │
├──────────────────────────────────────────────────────┤
│  repository/                                          │
│    MAY import:     domain/                            │
│    MAY NOT import: service/, controller/              │
├──────────────────────────────────────────────────────┤
│  domain/                                              │
│    MAY import:     domain.enums/                      │
│    MAY NOT import: any other project package          │
└──────────────────────────────────────────────────────┘
```
