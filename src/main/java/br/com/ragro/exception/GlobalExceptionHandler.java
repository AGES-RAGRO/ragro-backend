package br.com.ragro.exception;

import br.com.ragro.controller.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            buildValidationErrorResponse(
                ex.getBindingResult().getFieldErrors().stream()
                    .map(
                        fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                    .collect(Collectors.joining("; ")),
                request));
  }

  @ExceptionHandler(BindException.class)
  public ResponseEntity<ErrorResponse> handleBindException(
      BindException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            buildValidationErrorResponse(
                ex.getBindingResult().getFieldErrors().stream()
                    .map(
                        fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                    .collect(Collectors.joining("; ")),
                request));
  }

  private ErrorResponse buildValidationErrorResponse(
      String errorMessage, HttpServletRequest request) {
    return ErrorResponse.builder()
        .timestamp(java.time.LocalDateTime.now())
        .status(HttpStatus.BAD_REQUEST.value())
        .error(errorMessage)
        .path(request.getRequestURI())
        .build();
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(
      BusinessException ex, HttpServletRequest request) {

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(
      ConflictException ex, HttpServletRequest request) {

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      NotFoundException ex, HttpServletRequest request) {

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ErrorResponse> handleUnauthorized(
      UnauthorizedException ex, HttpServletRequest request) {

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .error(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(
      ForbiddenException ex, HttpServletRequest request) {

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Acesso negado")
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
      MaxUploadSizeExceededException ex, HttpServletRequest request) {

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Arquivo muito grande. Tamanho máximo permitido: 5MB")
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(InternalServerException.class)
  public ResponseEntity<ErrorResponse> handleInternalServer(
      InternalServerException ex, HttpServletRequest request) {

    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }

  /**
   * Google integrations (Geocoding/Routes): QUOTA/TRANSIENT → 503 + Retry-After; INVALID_INPUT → 422;
   * else 500. The message never leaks the Google payload.
   */
  @ExceptionHandler(GoogleApiException.class)
  public ResponseEntity<ErrorResponse> handleGoogleApi(
      GoogleApiException ex, HttpServletRequest request) {
    HttpStatus status =
        switch (ex.getKind()) {
          case QUOTA, TRANSIENT -> HttpStatus.SERVICE_UNAVAILABLE;
          case INVALID_INPUT -> HttpStatus.UNPROCESSABLE_ENTITY;
          case UNAVAILABLE -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(status.value())
            .error(ex.getMessage())
            .path(request.getRequestURI())
            .build();
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
    if (ex.getKind() == GoogleApiException.Kind.QUOTA) {
      builder.header("Retry-After", "30");
    } else if (ex.getKind() == GoogleApiException.Kind.TRANSIENT) {
      builder.header("Retry-After", "5");
    }
    return builder.body(response);
  }

  /**
   * Optimistic concurrency conflict (e.g. concurrent confirmations debiting the same product's stock).
   * The losing transaction gets a 409 and the client retries against the updated state.
   */
  @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ErrorResponse> handleOptimisticLock(
      org.springframework.orm.ObjectOptimisticLockingFailureException ex,
      HttpServletRequest request) {
    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error("Outra operação atualizou estes dados ao mesmo tempo. Tente novamente.")
            .path(request.getRequestURI())
            .build();
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }

  /**
   * Database integrity conflict (e.g. concurrent POST /routes from the same producer violating
   * {@code uq_delivery_routes_farmer_active}). Losing transaction rolls back; client gets a 409.
   */
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
      org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
    // Log only the cause type, never the raw DB message (may contain PII: email/values/SQL).
    log.warn(
        "Data integrity conflict at {} {} ({})",
        request.getMethod(),
        request.getRequestURI(),
        ex.getMostSpecificCause() != null
            ? ex.getMostSpecificCause().getClass().getSimpleName()
            : ex.getClass().getSimpleName());
    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error("Outra operação atualizou estes dados ao mesmo tempo. Tente novamente.")
            .path(request.getRequestURI())
            .build();
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }

  /**
   * Catch-all: every exception responds in the standard {@link ErrorResponse} envelope. Spring MVC
   * exceptions (malformed JSON, unsupported method, 404...) implementing
   * {@link org.springframework.web.ErrorResponse} keep their status; the rest become a generic 500,
   * logged with a stack trace and no internal details leaked to the client.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    if (ex instanceof org.springframework.web.ErrorResponse springError) {
      HttpStatusCode status = springError.getStatusCode();
      ErrorResponse response =
          ErrorResponse.builder()
              .timestamp(java.time.LocalDateTime.now())
              .status(status.value())
              .error(
                  status.is4xxClientError()
                      ? "Requisição inválida"
                      : "Erro ao processar a requisição")
              .path(request.getRequestURI())
              .build();
      return ResponseEntity.status(status).body(response);
    }

    log.error(
        "Unhandled exception at {} {}", request.getMethod(), request.getRequestURI(), ex);
    ErrorResponse response =
        ErrorResponse.builder()
            .timestamp(java.time.LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Erro interno do servidor")
            .path(request.getRequestURI())
            .build();
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }
}
