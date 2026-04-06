package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

  @NotBlank(message = "Phone is required")
  @Size(max = 20, message = "Phone must contain at most 20 characters")
  @Schema(
      description = "Phone number",
      example = "(51) 98765-4321",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String phone;

  @Valid
  @NotNull(message = "Address is required")
  @Schema(
      description = "Primary delivery address",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private AddressRequest address;
}
