package br.com.ragro.exception;

public class LlmInvalidOutputException extends BusinessException {

  public LlmInvalidOutputException(String message) {
    super(message);
  }

  public LlmInvalidOutputException(String message, Throwable cause) {
    super(message);
    initCause(cause);
  }
}
