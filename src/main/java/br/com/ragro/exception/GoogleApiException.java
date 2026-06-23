package br.com.ragro.exception;

/**
 * Typed failure for the Google API integrations (Geocoding/Routes). Message is safe to expose to the
 * customer (no payload/key leaked). {@code GlobalExceptionHandler} maps {@link Kind} to HTTP status.
 */
public class GoogleApiException extends RuntimeException {

  public enum Kind {
    /** Google quota/rate limit exceeded — the customer can retry later (503). */
    QUOTA,
    /** Non-routable/non-geocodable input (nonexistent address, invalid stop) — 422. */
    INVALID_INPUT,
    /**
     * Transient transport failure (DNS/connection/timeout) persisting after internal retries — 503 +
     * Retry-After. Distinct from {@link #UNAVAILABLE} (500) so non-retryable failures aren't marked retryable.
     */
    TRANSIENT,
    /** Unexpected integration error (key, contract, invalid response) — generic 500. */
    UNAVAILABLE
  }

  private final Kind kind;

  public GoogleApiException(Kind kind, String safeMessage) {
    super(safeMessage);
    this.kind = kind;
  }

  public GoogleApiException(Kind kind, String safeMessage, Throwable cause) {
    super(safeMessage, cause);
    this.kind = kind;
  }

  public Kind getKind() {
    return kind;
  }
}
