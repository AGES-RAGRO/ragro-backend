# RAGRO Backend Architecture — Error Handling

## Exception Hierarchy

The backend uses a set of custom exceptions that are caught globally by `GlobalExceptionHandler`:

```
RuntimeException
├── BusinessException       → 400 Bad Request
├── NotFoundException       → 404 Not Found
└── UnauthorizedException   → 401 Unauthorized
```

---

## Exception Classes

### BusinessException

Thrown when a business rule is violated.

**Examples:**
- Email already registered
- CognitoSub already exists
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
- User not found in database for given cognitoSub/email
- User account is inactive

```java
throw new UnauthorizedException("User not found for the given token");
```

**HTTP Response:** `401 Unauthorized`

---

## GlobalExceptionHandler

The `@RestControllerAdvice` class `GlobalExceptionHandler` catches all custom exceptions and returns a standardized `ErrorResponse`:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest req) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }
}
```

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

To add a new exception type:

1. Create the exception class in `br.com.ragro.exception`:
   ```java
   public class ForbiddenException extends RuntimeException {
       public ForbiddenException(String message) {
           super(message);
       }
   }
   ```

2. Add a handler method in `GlobalExceptionHandler`:
   ```java
   @ExceptionHandler(ForbiddenException.class)
   public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest req) {
       return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), req);
   }
   ```

---

## Spring Security Errors

Errors that occur **before** reaching the controller (e.g., invalid JWT, missing token) are handled by Spring Security's default mechanisms:

| Scenario | HTTP Code | Body |
|----------|-----------|------|
| Missing or malformed JWT | `401` | Spring default error body |
| Insufficient role | `403` | Spring default error body |
| Valid JWT, user not in DB | `401` | `ErrorResponse` via `UnauthorizedException` |
