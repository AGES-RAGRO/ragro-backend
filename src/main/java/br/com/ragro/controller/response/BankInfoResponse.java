package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Dados bancários do produtor para pagamento do pedido")
public class BankInfoResponse {

  @Schema(description = "Nome do banco", example = "Banco do Brasil")
  private String bankName;

  @Schema(description = "Código do banco", example = "001")
  private String bankCode;

  @Schema(description = "Agência", example = "3452-X")
  private String agency;

  @Schema(description = "Número da conta", example = "123456-7")
  private String account;

  @Schema(description = "Tipo da conta: checking ou savings", example = "checking")
  private String accountType;

  @Schema(description = "Chave PIX", example = "joao@email.com")
  private String pixKey;

  @Schema(description = "Tipo da chave PIX", example = "email")
  private String pixType;

  @Schema(description = "Nome do titular", example = "João Silva")
  private String holderName;
}
