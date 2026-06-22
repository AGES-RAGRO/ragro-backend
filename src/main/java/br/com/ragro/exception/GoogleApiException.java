package br.com.ragro.exception;

/**
 * Falha tipada nas integrações com as APIs do Google (Geocoding/Routes). A mensagem é segura para
 * o cliente (sem vazar payload/chave); o detalhe técnico vai para o log no ponto de origem. O
 * {@code GlobalExceptionHandler} mapeia {@link Kind} para o status HTTP adequado.
 */
public class GoogleApiException extends RuntimeException {

  public enum Kind {
    /** Quota/rate limit do Google estourado — o cliente pode tentar de novo depois (503). */
    QUOTA,
    /** Entrada não roteável/geocodável (endereço inexistente, parada inválida) — 422. */
    INVALID_INPUT,
    /**
     * Falha transitória de transporte (resolução DNS/conexão/timeout) ao chamar o Google,
     * persistente após os retries internos — o cliente pode tentar de novo em instantes (503 +
     * Retry-After). Distingue-se de {@link #UNAVAILABLE} (key/contrato/erro inesperado, 500) para
     * não rotular como retryável uma falha que não vai se resolver sozinha.
     */
    TRANSIENT,
    /** Erro inesperado da integração (key, contrato, resposta inválida) — 500 genérico. */
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
