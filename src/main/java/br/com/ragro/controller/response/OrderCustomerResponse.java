package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Resumo do cliente embutido no {@link OrderResponse} (visão do produtor). O app renderiza
 * telefone e "cliente desde" no detalhe do pedido; antes estes dados não eram enviados e a UI
 * degradava para vazio. Não há foto de cliente no domínio — o app usa placeholder.
 */
@Getter
@Builder
@Schema(description = "Resumo do cliente do pedido (visão do produtor)")
public class OrderCustomerResponse {

  @Schema(description = "Nome do cliente", example = "Maria Silva")
  private String name;

  @Schema(description = "Telefone do cliente (11 dígitos)", example = "51987654321")
  private String phone;

  @Schema(description = "Data de criação da conta do cliente")
  private OffsetDateTime memberSince;
}
