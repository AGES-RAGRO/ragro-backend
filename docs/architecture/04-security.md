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

The central security configuration (`SecurityConfig.java`) defines:

- **CSRF**: Disabled (stateless API)
- **Session management**: `STATELESS` — no server-side sessions
- **CORS**: Enabled via `CorsConfig`
- **Authorization rules**:

```
/admin/**    → requires ROLE_ADMIN
/farmer/**   → requires ROLE_FARMER
/customers/** → requires ROLE_CUSTOMER
All other    → requires authentication (any role)
```

- **OAuth2 Resource Server**: JWT-based with a custom authority converter

---

### KeycloakRolesConverter

A custom `Converter<Jwt, Collection<GrantedAuthority>>` that:

1. Extracts the `groups` claim from the JWT
2. Filters out null/blank values
3. Converts each group to uppercase
4. Prefixes with `ROLE_` (e.g., `ADMIN` → `ROLE_ADMIN`)

This converter is composed with the default scopes converter using a `DelegatingJwtGrantedAuthoritiesConverter`.

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
2. If not found, fall back to `email` (secondary lookup)
3. If neither matches, throw `UnauthorizedException`

---

## Keycloak Realm Setup

The Keycloak realm `ragro` is pre-configured via `keycloak/ragro-realm.json`:

| Configuration | Value |
|---------------|-------|
| Login attribute | Email (`loginWithEmailAllowed: true`) |
| Groups | `ADMIN`, `FARMER`, `CUSTOMER` |
| Client | `ragro-app` (public, Direct Access Grants enabled) |
| Password policy | Min 8 chars, 1 lowercase, 1 uppercase, 1 digit |
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

1. **URL pattern** — if it follows `/admin/**`, `/farmer/**`, or `/customers/**`, it is automatically protected by the existing rules
2. **Custom pattern** — add a new matcher in `SecurityConfig.java`:
   ```java
   .requestMatchers("/new-path/**").hasRole("REQUIRED_ROLE")
   ```
3. **JWT access** — inject `@AuthenticationPrincipal Jwt jwt` in the controller method to access token claims
