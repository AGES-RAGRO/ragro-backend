package br.com.ragro.controller.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Optional payload accepted by order cancellation endpoints. Both fields are nullable so the
 * endpoints stay backward compatible with clients that send no body.
 */
@Getter
@Setter
public class CancelOrderRequest {

  @Size(max = 100, message = "O motivo deve ter no máximo 100 caracteres")
  private String reason;

  @Size(max = 1000, message = "Os detalhes devem ter no máximo 1000 caracteres")
  private String details;
}
