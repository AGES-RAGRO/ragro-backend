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
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/session")
    public ResponseEntity<SessionResponse> getSession(@AuthenticationPrincipal Jwt jwt) {
        User user = userService.getAuthenticatedUser(jwt);
        return ResponseEntity.ok(SessionResponse.builder()
            .id(user.getId()).name(user.getName())
            .email(user.getEmail()).type(user.getType().name().toLowerCase())
            .active(user.isActive()).build());
    }
}
```

**Rules:**
- Controllers must not contain business logic
- Controllers must not call repositories directly
- One controller per domain/resource
- Controllers may reference domain entities/enums (e.g. `User`, `OrderStatus`) as method types, but must not expose them in HTTP response bodies

> **WebSocket flavor:** `TrackingWsController` is a STOMP `@Controller` (not `@RestController`). It handles `@MessageMapping("/routes/{routeId}/position")`, returns `void`, and rebroadcasts accepted positions via `SimpMessagingTemplate` rather than returning a response DTO. The "never return JPA entities" rule still applies, but the HTTP request/response shape above does not.

---

### Service Layer

Contains all business logic and orchestration:

- Enforcing business rules (e.g., "email must be unique")
- Coordinating between multiple repositories
- Extracting and validating JWT claims
- Managing transactions (`@Transactional`)

```java
@Service
public class CustomerRegistrationService {

    @Transactional
    public CustomerRegistrationResponse register(CustomerRegistrationRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException(REGISTRATION_CONFLICT_MESSAGE);
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
- Entities represent the database schema — keep them aligned with Flyway migrations in `src/main/resources/db/migration`
- Hibernate DDL is set to `validate` — entities must match the existing schema exactly
- Enums use `@Enumerated(EnumType.STRING)` for readability

---

### Mapper Layer

Dedicated classes for converting between entities and DTOs:

```java
@UtilityClass
public class CustomerMapper {

    public static Customer toEntity(User user, String fiscalNumber) {
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFiscalNumber(fiscalNumber);
        return customer;
    }

    public static CustomerResponse toResponse(User user) {
        return CustomerResponse.builder()
            .id(user.getId())
            .name(user.getName())
            .email(user.getEmail())
            // ...
            .build();
    }
}
```

**Rules:**
- Mappers are utility classes with static methods
- One mapper per domain entity
- Mappers handle null checks gracefully

---

### Ports & Adapters (service/api + service/impl)

A few capabilities are isolated behind a hexagonal-style port/adapter split:

- `service/api` holds the port interfaces: `LlmRerankerPort`, `PositionStore`, `IdentityProviderService`.
- `service/impl` holds the adapters: `SpringAiRerankerAdapter` / `DisabledRerankerAdapter` (NVIDIA reranker via Spring AI, toggled by configuration), `InMemoryPositionStore` (live GPS positions for tracking), and `KeycloakIdentityProviderService`.

**Rules:**
- Regular services depend on the port interface in `service/api`, never on a concrete adapter
- Adapters in `service/impl` are the only place where the external integration (Spring AI client, in-memory store, Keycloak admin client) is wired up

---

### Event & Listener Layer

The application uses Spring application events for decoupled, post-commit side effects:

- Event types live in `event/` (`OrderPushNotificationEvent`) and `domain/event/` (`OrderStatusChangedEvent`).
- Listeners react to them: `listener/PushNotificationListener` sends FCM push, while `service/OrderNotificationListener` and `service/RouteStopSyncListener` handle notification persistence and route-stop synchronization.

**Rules:**
- Listeners run after the publishing transaction commits, so failures in a side effect do not roll back the originating business operation
- Publish an event instead of calling another service directly when the work is a fire-and-forget side effect (push, sync)

---

### Validation Layer

Custom Jakarta Bean Validation constraints live in `validation/`:

- Constraint annotations: `ValidCpf`, `ValidFiscalNumber`.
- Validator implementations: `CpfValidator`, `CnpjValidator`, `FiscalNumberValidator`.

These back the `@Valid @RequestBody` validation that controllers trigger, so the format/check-digit rules for CPF and fiscal numbers are enforced declaratively on request DTOs.

**Rules:**
- Annotate request DTO fields with the custom constraint; the matching `ConstraintValidator` runs automatically
- Validators contain only format/structural checks — uniqueness and other business rules stay in the service layer

---

## Layer Dependency Rules

```
┌──────────────────────────────────────────────────────┐
│  controller/                                          │
│    MAY import:     service/, controller.request/,     │
│                    controller.response/,               │
│                    domain/ + domain.enums/ (as types)  │
│    MAY NOT import: repository/                         │
├──────────────────────────────────────────────────────┤
│  service/                                             │
│    MAY import:     repository/, domain/, mapper/,     │
│                    exception/, event/,                 │
│                    controller.request/,                │
│                    controller.response/                │
│    MAY NOT import: controller (REST classes)          │
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
