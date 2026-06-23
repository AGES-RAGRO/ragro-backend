package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Payload to update the authenticated customer profile")
public class CustomerUpdateRequest {

  @NotBlank(message = "Name is required")
  @Schema(
      description = "Full name of the customer",
      example = "João Silva",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  // 11 raw digits, same rule as registration and producer update.
  @NotBlank(message = "Phone is required")
  @Pattern(regexp = "^\\d{11}$", message = "Phone must contain exactly 11 digits")
  @Schema(
      description = "Phone number (digits only)",
      example = "51987654321",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String phone;

  @Valid
  @NotNull(message = "Address is required")
  @Schema(description = "Primary delivery address", requiredMode = Schema.RequiredMode.REQUIRED)
  private AddressRequest address;
}
