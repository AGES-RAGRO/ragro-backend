package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Payment method data (pix or bank_account). Send only the fields relevant to the type.")
public class PaymentMethodRequest {

  @NotBlank(message = "Payment method type is required")
  @Pattern(regexp = "pix|bank_account", message = "Type must be 'pix' or 'bank_account'")
  @Schema(description = "Payment method type", example = "pix", allowableValues = {"pix", "bank_account"}, requiredMode = Schema.RequiredMode.REQUIRED)
  private String type;

  // ── PIX ──────────────────────────────────────────────────────────────────────

  @Pattern(
      regexp = "cpf|cnpj|email|phone|random",
      message = "PIX key type must be one of: cpf, cnpj, email, phone, random")
  @Schema(description = "PIX key type", example = "email")
  private String pixKeyType;

  @Size(max = 100, message = "PIX key must contain at most 100 characters")
  @Schema(description = "PIX key value", example = "joao@email.com")
  private String pixKey;

  // ── Bank account ─────────────────────────────────────────────────────────────

  @Size(max = 3, message = "Bank code must contain at most 3 characters")
  @Schema(description = "Bank ISPB/code (3 digits)", example = "001")
  private String bankCode;

  @Size(max = 100, message = "Bank name must contain at most 100 characters")
  @Schema(description = "Bank name", example = "Banco do Brasil")
  private String bankName;

  @Size(max = 10, message = "Agency must contain at most 10 characters")
  @Schema(description = "Bank agency number", example = "3452-X")
  private String agency;

  @Size(max = 20, message = "Account number must contain at most 20 characters")
  @Schema(description = "Bank account number", example = "123456-7")
  private String accountNumber;

  @Pattern(
      regexp = "checking|savings",
      message = "Account type must be 'checking' or 'savings'")
  @Schema(description = "Account type", example = "checking", allowableValues = {"checking", "savings"})
  private String accountType;

  @Size(max = 120, message = "Holder name must contain at most 120 characters")
  @Schema(description = "Account holder name", example = "João Silva")
  private String holderName;

  @Size(max = 14, message = "Fiscal number must contain at most 14 characters")
  @Schema(description = "CPF or CNPJ of the account holder", example = "12345678901")
  private String fiscalNumber;
}
