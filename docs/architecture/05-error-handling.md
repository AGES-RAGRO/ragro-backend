# RAGRO Backend Architecture — Error Handling

## Exception Hierarchy

The backend uses a set of custom exceptions (in `br.com.ragro.exception`) that are caught globally by `GlobalExceptionHandler`:

```
RuntimeException
├── BusinessException          → 400 Bad Request
│   └── LlmInvalidOutputException → 400 (LLM/recommendation output validation)
├── NotFoundException          → 404 Not Found
├── UnauthorizedException      → 401 Unauthorized
├── ForbiddenException         → 403 Forbidden
├── ConflictException          → 409 Conflict
├── InternalServerException    → 500 Internal Server Error
└── GoogleApiException         → 503 / 422 / 500 (mapped from its Kind enum)
```

`LlmInvalidOutputException` extends `BusinessException` (not `RuntimeException` directly), so it also resolves to `400`.

`GoogleApiException` carries a `Kind` enum that `GlobalExceptionHandler` maps to status:

| `Kind` | HTTP status | Notes |
|--------|-------------|-------|
| `QUOTA` | `503` | + `Retry-After: 30` |
| `TRANSIENT` | `503` | + `Retry-After: 5` |
| `INVALID_INPUT` | `422` | non-routable/non-geocodable input |
| `UNAVAILABLE` | `500` | unexpected integration error |

---

## Exception Classes

### BusinessException

Thrown when a business rule is violated.

**Examples:**
- Email already registered
- AuthSub already exists
- Invalid stock quantity
- Cart from different farmer

```java
throw new BusinessException("Email already registered");
```

**HTTP Response:** `400 Bad Request`

---

### NotFoundException

Thrown when a requested resource does not exist.

**Examples:**
- User not found by ID
- Product not found
- Order not found

```java
throw new NotFoundException("User not found");
```

**HTTP Response:** `404 Not Found`

---

### UnauthorizedException

Thrown when authentication or authorization fails at the application level.

**Examples:**
- JWT missing required claims (sub, email)
- User not found in database for given authSub/email
- User account is inactive

```java
throw new UnauthorizedException("User not found for the given token");
```

**HTTP Response:** `401 Unauthorized`

---

### ForbiddenException

Thrown when an authenticated user lacks permission for a specific action at the application level.

```java
throw new ForbiddenException("Acesso negado");
```

**HTTP Response:** `403 Forbidden`

---

### ConflictException

Thrown when a request conflicts with the current state of a resource (uniqueness or state conflicts).

```java
throw new ConflictException("Já existe uma rota ativa para este produtor");
```

**HTTP Response:** `409 Conflict`

---

### InternalServerException

Thrown for unrecoverable server-side failures that should surface as a `500`.

```java
throw new InternalServerException("Falha inesperada");
```

**HTTP Response:** `500 Internal Server Error`

---

### LlmInvalidOutputException

Extends `BusinessException`. Thrown in the LLM/recommendation flow when the model output fails validation.

```java
throw new LlmInvalidOutputException("LLM returned invalid output");
```

**HTTP Response:** `400 Bad Request`

---

### GoogleApiException

Typed failure for the Google integrations (Geocoding / Routes). Carries a `Kind` enum
(`QUOTA`, `TRANSIENT`, `INVALID_INPUT`, `UNAVAILABLE`) that the handler maps to a status; the
message is safe to expose (no payload or API key leaked).

```java
throw new GoogleApiException(GoogleApiException.Kind.INVALID_INPUT, "Endereço não encontrado");
```

**HTTP Response:** `503` (`QUOTA`/`TRANSIENT`, with `Retry-After`) · `422` (`INVALID_INPUT`) · `500` (`UNAVAILABLE`)

---

## GlobalExceptionHandler

The `@RestControllerAdvice` class `GlobalExceptionHandler` catches every exception and returns a standardized `ErrorResponse`. It declares 13 `@ExceptionHandler` methods (custom exceptions, bean-validation failures, Spring Security authorization, upload-size, concurrency conflicts, the Google integration, plus a catch-all) and uses SLF4J logging for data-integrity and unhandled exceptions:

| Handler | Catches | HTTP status | Message |
|---------|---------|-------------|---------|
| `handleValidation` | `MethodArgumentNotValidException` | `400` | field errors joined by `"; "` (e.g. `email: must not be blank; price: must be positive`) |
| `handleBindException` | `BindException` | `400` | same field-error format as above |
| `handleBusinessException` | `BusinessException` (and subclasses, e.g. `LlmInvalidOutputException`) | `400` | exception message |
| `handleConflict` | `ConflictException` | `409` | exception message |
| `handleNotFound` | `NotFoundException` | `404` | exception message |
| `handleUnauthorized` | `UnauthorizedException` | `401` | exception message |
| `handleForbidden` | `ForbiddenException` | `403` | exception message |
| `handleAccessDenied` | `AccessDeniedException` (Spring Security) | `403` | `"Acesso negado"` |
| `handleMaxUploadSizeExceeded` | `MaxUploadSizeExceededException` | `400` | `"Arquivo muito grande. Tamanho máximo permitido: 5MB"` |
| `handleInternalServer` | `InternalServerException` | `500` | exception message |
| `handleGoogleApi` | `GoogleApiException` | `503` / `422` / `500` | safe message; adds `Retry-After` on `QUOTA` (30s) / `TRANSIENT` (5s) |
| `handleOptimisticLock` | `ObjectOptimisticLockingFailureException` | `409` | `"Outra operação atualizou estes dados ao mesmo tempo. Tente novamente."` |
| `handleDataIntegrityViolation` | `DataIntegrityViolationException` | `409` | same retry message; logs the cause type only (never the raw DB message) |
| `handleUnexpected` | `Exception` (catch-all) | preserves Spring MVC status, else `500` | `"Requisição inválida"` / `"Erro ao processar a requisição"` (Spring MVC) or `"Erro interno do servidor"` (logged with stack trace) |

`ErrorResponse` lives in `br.com.ragro.controller.response` (Lombok `@Builder`/`@Getter`/`@AllArgsConstructor`). Most error messages are in Portuguese (PT-BR).

---

## Error Response Format

All error responses follow the same structure:

```json
{
  "timestamp": "2026-03-30T12:00:00",
  "status": 400,
  "error": "Email already registered",
  "path": "/admin/users"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | `LocalDateTime` | When the error occurred |
| `status` | `int` | HTTP status code |
| `error` | `String` | Error message from the exception |
| `path` | `String` | Request URI that caused the error |

---

## Adding New Exceptions

To add a new exception type (the existing classes above already cover `400`/`401`/`403`/`404`/`409`/`500`):

1. Create the exception class in `br.com.ragro.exception`:
   ```java
   public class PaymentRequiredException extends RuntimeException {
       public PaymentRequiredException(String message) {
           super(message);
       }
   }
   ```

2. Add a handler method in `GlobalExceptionHandler` that builds the standard `ErrorResponse`:
   ```java
   @ExceptionHandler(PaymentRequiredException.class)
   public ResponseEntity<ErrorResponse> handlePaymentRequired(
       PaymentRequiredException ex, HttpServletRequest request) {
       ErrorResponse response =
           ErrorResponse.builder()
               .timestamp(java.time.LocalDateTime.now())
               .status(HttpStatus.PAYMENT_REQUIRED.value())
               .error(ex.getMessage())
               .path(request.getRequestURI())
               .build();
       return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
   }
   ```

---

## Spring Security Errors

| Scenario | HTTP Code | Body |
|----------|-----------|------|
| Missing or malformed JWT | `401` | Spring Security default (authentication entry point — runs before `@RestControllerAdvice`) |
| Insufficient role (`AccessDeniedException`) | `403` | `ErrorResponse` (`"Acesso negado"`) via `GlobalExceptionHandler.handleAccessDenied` |
| Valid JWT, user not in DB | `401` | `ErrorResponse` via `UnauthorizedException` |
| Unhandled Spring MVC error (malformed JSON, unsupported method, framework 404…) | preserves the Spring status | `ErrorResponse` via the catch-all `handleUnexpected` |

---

## Filter-level Errors

Two servlet filters in `br.com.ragro.config` run **before** the `@RestControllerAdvice` and write the same `ErrorResponse` JSON shape (`timestamp` / `status` / `error` / `path`) directly to the response:

| Filter | Scenario | HTTP Code | Body |
|--------|----------|-----------|------|
| `RateLimitFilter` | Per-route rate limit exceeded (e.g. `POST /auth/register/*`, `/auth/password/forgot`) | `429` | `"Muitas requisições. Tente novamente em instantes."` + `Retry-After` header |
| `ActiveUserFilter` | Authenticated user is deactivated or not found in the DB | `401` | `"Conta desativada ou usuário não encontrado"` |
