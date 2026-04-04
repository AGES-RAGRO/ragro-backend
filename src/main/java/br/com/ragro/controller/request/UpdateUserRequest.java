package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request payload for updating user profile")
public class UpdateUserRequest {

  @NotBlank(message = "Name is required")
  @Schema(
      description = "Full name of the user",
      example = "João Silva",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  @Pattern(regexp = "^\\d{11}$", message = "Phone must contain 11 digits (DDD + number)")
  @Schema(description = "Brazilian phone number: 2-digit area code + 9 digits (numbers only)", example = "51987654321")
  private String phone;
}
