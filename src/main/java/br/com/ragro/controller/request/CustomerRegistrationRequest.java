package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request payload for customer registration")
public class CustomerRegistrationRequest {

  @NotBlank(message = "Name is required")
  @Schema(
      description = "Full name of the customer",
      example = "João Silva",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  @NotBlank(message = "Phone is required")
  @Pattern(regexp = "^\\d{11}$", message = "Phone must contain 11 digits (DDD + number)")
  @Schema(
      description = "Brazilian phone number: 2-digit area code + 9 digits (numbers only)",
      example = "51987654321",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String phone;

  @NotBlank(message = "Email is required")
  @Email(message = "Email should be valid")
  @Schema(
      description = "Email address (must be unique)",
      example = "joao@example.com",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String email;

  @NotBlank(message = "Password is required")
  @Size(min = 8, max = 50, message = "Password must contain between 8 and 50 characters")
  @Pattern(
      regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
      message =
          "Password must contain at least one uppercase letter, one lowercase letter, and one digit")
  @Schema(
      description = "Password (min 8 chars, must contain uppercase, lowercase, and digit)",
      example = "Senha@123",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String password;

  @NotBlank(message = "Fiscal number is required")
  @Pattern(regexp = "^\\d{11}$", message = "Fiscal number must contain 11 digits")
  @Schema(
      description = "CPF (11 digits, numbers only)",
      example = "12345678901",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String fiscalNumber;

  @Valid
  @NotNull(message = "Address is required")
  @Schema(
      description = "Primary address for the customer",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private AddressRequest address;
}
