package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Active payment method of the producer")
public class PaymentMethodResponse {

  private UUID id;

  @Schema(description = "Payment type: pix or bank_account", example = "pix")
  private String type;

  @Schema(description = "PIX key type", example = "email")
  private String pixKeyType;

  @Schema(description = "PIX key", example = "joao@email.com")
  private String pixKey;

  @Schema(description = "Bank code", example = "001")
  private String bankCode;

  @Schema(description = "Bank name", example = "Banco do Brasil")
  private String bankName;

  @Schema(description = "Agency number", example = "3452-X")
  private String agency;

  @Schema(description = "Account number", example = "123456-7")
  private String accountNumber;

  @Schema(description = "Account type: checking or savings", example = "checking")
  private String accountType;

  @Schema(description = "Holder name", example = "João Silva")
  private String holderName;
}
