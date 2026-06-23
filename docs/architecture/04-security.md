# RAGRO Backend Architecture — Security

## Authentication Flow

```
┌──────────────┐     ┌──────────────┐     ┌──────────────────────┐
│  Mobile App  │────▶│  Keycloak    │────▶│  RAGRO Backend       │
│              │     │              │     │                      │
│  1. Login    │     │  2. Validate │     │  4. Validate JWT     │
│     form     │     │     creds    │     │  5. Extract groups   │
│              │◀────│  3. Return   │     │  6. Map to ROLE_X    │
│  Store JWT   │     │     JWT      │     │  7. Authorize route  │
└──────────────┘     └──────────────┘     └──────────────────────┘
```

### Step-by-step:

1. **User submits credentials** — email and password sent to Keycloak (Direct Access Grants / Resource Owner Password)
2. **Keycloak validates** — checks credentials against the `ragro` realm
3. **Keycloak returns JWT** — token contains `sub`, `email`, and `groups`
4. **Backend validates JWT** — Spring Security verifies the signature using Keycloak's JWKS endpoint
5. **Groups extracted** — `KeycloakRolesConverter` reads `groups` from the JWT
6. **Roles mapped** — each group becomes a `ROLE_X` authority (e.g., `ADMIN` → `ROLE_ADMIN`)
7. **Route authorized** — `SecurityConfig` checks if the user has the required role

---

## Security Configuration

### SecurityConfig

The central security configuration (`SecurityConfig.java`) is annotated with `@EnableMethodSecurity` and defines **two** `SecurityFilterChain` beans:

- **CSRF**: Disabled on both chains (stateless API)
- **Session management**: `STATELESS` on both chains — no server-side sessions
- **CORS**: Enabled on both chains via `CorsConfig`

#### Public chain (`@Order(1)`)

`publicSecurityFilterChain` matches `PUBLIC_MATCHERS` and applies `anyRequest().permitAll()`. Crucially, this chain is registered **without** the OAuth2 resource server, so a presented JWT is ignored on these routes. This is deliberate: a stale/expired token must not produce a `401` on a `permitAll` route (this is why `register`/`forgot`/`config` no longer fail with a stale token). `/error` is included so an unhandled exception surfaces its real status instead of a misleading `401` (Spring Security filters the `ERROR` dispatch by default).

The `PUBLIC_MATCHERS` allowlist is:

```
/error
/actuator/health
/media/**
/auth/register/customer
/auth/password/forgot
/auth/config
/v3/api-docs, /v3/api-docs/**
/swagger-ui, /swagger-ui.html, /swagger-ui/**
/swagger-resources, /swagger-resources/**
/webjars/**
/co2/options, /co2/total-saved, /co2/calculate
```

Note `/co2/record-savings` is **not** public — it is authenticated.

#### Authenticated chain (`@Order(2)`)

`securityFilterChain` covers everything not matched by the public chain. It enables the **OAuth2 Resource Server** (JWT-based with a custom authority converter) and adds `ActiveUserFilter` after `BearerTokenAuthenticationFilter`. Its URL authorization rules (evaluated top-down) are:

```
/admin/**                          → ROLE_ADMIN
GET  /search                       → ROLE_CUSTOMER
POST /reviews                      → ROLE_CUSTOMER
GET  /producers/locations          → authenticated
GET  /producers                    → ROLE_CUSTOMER
GET  /producers/*/profile          → ROLE_CUSTOMER
GET  /producers/*/products         → ROLE_CUSTOMER
GET  /producers/*/products/*       → ROLE_CUSTOMER
GET  /producers/*/reviews          → ROLE_CUSTOMER or ROLE_FARMER
GET  /producers/stock/*/movements  → ROLE_FARMER
/producers/**                      → ROLE_FARMER (catch-all)
/customers/**                      → ROLE_CUSTOMER
All other                          → requires authentication (any role)
```

> `/producers/**` is **not** uniformly `ROLE_FARMER`: several `GET` sub-paths require `ROLE_CUSTOMER` (or `CUSTOMER`+`FARMER`); only paths not matched by an earlier rule fall back to `ROLE_FARMER`.

---

### Method-level security (@PreAuthorize)

URL-pattern rules in `SecurityConfig` are only one half of access control. Because `SecurityConfig` enables `@EnableMethodSecurity`, per-endpoint authorization is also enforced with `@PreAuthorize` annotations on controllers (13 controllers): `AdminController` (`hasRole('ADMIN')`), `RouteController`, `StockController`, `ProducerNotificationController` (`hasRole('FARMER')`), `CustomerController`, `FavoriteProducerController`, `ReviewController`, `RecommendationController`, `SearchController`, `OrderTrackingController`, `CustomerNotificationController` (`hasRole('CUSTOMER')`), `ProducerController` (mixed `CUSTOMER`/`FARMER`/`ADMIN` per method), and `NotificationController` (`isAuthenticated()`). This `@PreAuthorize` style is the de-facto pattern for new endpoints; the URL matchers act as a coarse backstop.

---

### ActiveUserFilter

`ActiveUserFilter` is a custom `OncePerRequestFilter` registered on the authenticated chain after `BearerTokenAuthenticationFilter`. For requests carrying a `JwtAuthenticationToken`, it loads the `User` by the `sub` claim and:

- Returns `401` with body `{"error": "Conta desativada ou usuário não encontrado", ...}` if the user is missing or `users.active = false`.
- Otherwise stashes the resolved `User` as the `authenticatedUser` request attribute for downstream use.

This enforces account-deactivation at the request boundary (the `users.active` column defaults to `true`).

---

### KeycloakRolesConverter

A custom `Converter<Jwt, Collection<GrantedAuthority>>` that:

1. Extracts the `groups` claim from the JWT
2. Filters out null/blank values
3. Converts each group to uppercase
4. Prefixes with `ROLE_` (e.g., `ADMIN` → `ROLE_ADMIN`)

This converter is composed with the default scopes converter using a `DelegatingJwtGrantedAuthoritiesConverter`.

---

### CorsConfig

`CorsConfig` registers a single `CorsConfigurationSource` for `/**` with these security-relevant settings:

- **Allowed origin patterns**: configurable via `cors.allowed-origin-patterns` (comma-separated, wildcards allowed); default `http://localhost:*`. In prod this must include the public API Gateway and frontend URLs.
- **Allowed methods**: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `PATCH`
- **Allowed headers**: `Content-Type`, `Authorization`
- **Exposed headers**: `Authorization`
- **Allow credentials**: `false` — deliberate. The API is a stateless bearer-token resource server (no cookies), so credentialed CORS is unnecessary, and keeping it `false` avoids the wildcard-origin-pattern + credentials combination.
- **Max age**: `3600` seconds

---

## Keycloak ↔ Database Bridge

The `auth_sub` field in the `users` table acts as the bridge between Keycloak and the application database:

```
JWT Token                    Database
┌─────────────────┐         ┌──────────────────┐
│ sub: "abc-123"  │────────▶│ auth_sub:        │
│ email: "x@y.z"  │         │   "abc-123"      │
│ groups: [ADMIN]  │         │ email: "x@y.z"   │
└─────────────────┘         └──────────────────┘
```

**User resolution strategy** (in `UserService`):

1. First, try to find user by `authSub` (primary lookup)
2. If not found, fall back to `email` (secondary lookup). On a match, the record's `auth_sub` is **self-healed** (back-filled with the JWT `sub`) and saved, so future lookups hit the fast path
3. If neither matches, throw `UnauthorizedException`

---

## Keycloak Realm Setup

The Keycloak realm `ragro` is pre-configured via `keycloak/ragro-realm.json`:

| Configuration | Value |
|---------------|-------|
| Login attribute | Email (`loginWithEmailAllowed: true`) |
| Groups | `ADMIN`, `CUSTOMER`, `FARMER` |
| Client | `ragro-app` (public, Direct Access Grants enabled) |
| Self-registration | Disabled (`registrationAllowed: false`) |
| Password policy | Min 8 chars, 1 lowercase, 1 uppercase, 1 digit |
| SSL required | `external` |
| Brute-force protection | Enabled (`failureFactor: 5`, `waitIncrementSeconds: 60`, `maxFailureWaitSeconds: 900`, `permanentLockout: false`) |
| JWT claims | `sub`, `email`, `groups` (via group membership mapper) |

**JWKS endpoint** (configured in `application.yml`):
```
http://localhost:8180/realms/ragro/protocol/openid-connect/certs
```

### User Registration via Admin REST API

The `KeycloakIdentityProviderService` uses the Keycloak Admin REST API to register users:

1. Obtain admin token from master realm (`admin-cli` client)
2. Create user in `ragro` realm (with email, groups, emailVerified)
3. Set password via separate `reset-password` endpoint
4. If password set fails, delete the orphaned Keycloak user

The `CustomerRegistrationService` wraps this in a compensating transaction: if the DB save fails after Keycloak creation, the Keycloak user is deleted to prevent orphans.

---

## Adding New Protected Endpoints

When adding a new endpoint that requires role-based access:

1. **Method-level (preferred)** — annotate the controller method (or class) with `@PreAuthorize`, e.g. `@PreAuthorize("hasRole('CUSTOMER')")` or `@PreAuthorize("isAuthenticated()")`. This is the de-facto pattern for new endpoints (see [Method-level security](#method-level-security-preauthorize)).
2. **URL pattern** — `/admin/**` and `/customers/**` map cleanly to `ROLE_ADMIN`/`ROLE_CUSTOMER`. **Do not** assume `/producers/**` is `ROLE_FARMER`: several `GET` sub-paths are scoped to `ROLE_CUSTOMER`, so add an explicit, more-specific matcher (ordered above the `/producers/**` catch-all) or rely on `@PreAuthorize`.
3. **Custom matcher** — add it in `SecurityConfig.java`:
   ```java
   .requestMatchers("/new-path/**").hasRole("REQUIRED_ROLE")
   ```
4. **Public endpoint** — to make a route public, add its pattern to `PUBLIC_MATCHERS` in the `@Order(1)` chain (which has no OAuth2 resource server), not just `permitAll()` on the authenticated chain.
5. **JWT access** — inject `@AuthenticationPrincipal Jwt jwt` in the controller method to access token claims (or read the `authenticatedUser` request attribute populated by `ActiveUserFilter`).
