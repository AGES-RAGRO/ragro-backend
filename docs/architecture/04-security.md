# RAGRO Backend Architecture — Security

## Authentication Flow

```
┌──────────────┐     ┌──────────────┐     ┌──────────────────────┐
│  Mobile App  │────▶│  AWS Cognito │────▶│  RAGRO Backend       │
│              │     │              │     │                      │
│  1. Login    │     │  2. Validate │     │  4. Validate JWT     │
│     form     │     │     creds    │     │  5. Extract groups   │
│              │◀────│  3. Return   │     │  6. Map to ROLE_X    │
│  Store JWT   │     │     JWT      │     │  7. Authorize route  │
└──────────────┘     └──────────────┘     └──────────────────────┘
```

### Step-by-step:

1. **User submits credentials** — email and password sent to Cognito
2. **Cognito validates** — checks credentials against the User Pool
3. **Cognito returns JWT** — token contains `sub`, `email`, and `cognito:groups`
4. **Backend validates JWT** — Spring Security verifies the signature using Cognito's JWKS endpoint
5. **Groups extracted** — `CognitoGroupsAuthoritiesConverter` reads `cognito:groups` from the JWT
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
/admin/**   → requires ROLE_ADMIN
/farmer/**  → requires ROLE_FARMER
/customer/** → requires ROLE_CUSTOMER
All other   → requires authentication (any role)
```

- **OAuth2 Resource Server**: JWT-based with a custom authority converter

---

### CognitoGroupsAuthoritiesConverter

A custom `Converter<Jwt, Collection<GrantedAuthority>>` that:

1. Extracts the `cognito:groups` claim from the JWT
2. Filters out null/blank values
3. Converts each group to uppercase
4. Prefixes with `ROLE_` (e.g., `admin` → `ROLE_ADMIN`)

This converter is composed with the default scopes converter using a `DelegatingJwtGrantedAuthoritiesConverter`.

---

## Cognito ↔ Database Bridge

The `cognito_sub` field in the `users` table acts as the bridge between AWS Cognito and the application database:

```
JWT Token                    Database
┌─────────────────┐         ┌──────────────────┐
│ sub: "abc-123"  │────────▶│ cognito_sub:     │
│ email: "x@y.z"  │         │   "abc-123"      │
│ groups: [ADMIN]  │         │ email: "x@y.z"   │
└─────────────────┘         └──────────────────┘
```

**User resolution strategy** (in `UserService`):

1. First, try to find user by `cognitoSub` (primary lookup)
2. If not found, fall back to `email` (secondary lookup)
3. If neither matches, throw `UnauthorizedException`

---

## Cognito User Pool Setup

The Cognito User Pool must have:

| Configuration | Value |
|---------------|-------|
| Login attribute | Email |
| Groups | `ADMIN`, `FARMER`, `CUSTOMER` |
| Password policy | Cognito defaults (or custom) |
| Token claims | `sub`, `email`, `cognito:groups` |

**JWKS endpoint** (configured in `application.yml`):
```
https://cognito-idp.<REGION>.amazonaws.com/<USER_POOL_ID>/.well-known/jwks.json
```

---

## Adding New Protected Endpoints

When adding a new endpoint that requires role-based access:

1. **URL pattern** — if it follows `/admin/**`, `/farmer/**`, or `/customer/**`, it is automatically protected by the existing rules
2. **Custom pattern** — add a new matcher in `SecurityConfig.java`:
   ```java
   .requestMatchers("/new-path/**").hasRole("REQUIRED_ROLE")
   ```
3. **JWT access** — inject `@AuthenticationPrincipal Jwt jwt` in the controller method to access token claims
