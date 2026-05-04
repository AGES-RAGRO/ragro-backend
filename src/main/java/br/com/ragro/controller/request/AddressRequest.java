package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Address information")
public class AddressRequest {

  @NotBlank(message = "Street is required")
  @Schema(
      description = "Street name",
      example = "Rua das Flores",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String street;

  @NotBlank(message = "Number is required")
  @Schema(
      description = "Street number",
      example = "123",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String number;

  @Schema(description = "Complementary address info (apt, suite, etc.)", example = "Apto 42")
  private String complement;

  @NotBlank(message = "Neighborhood is required")
  @Schema(
      description = "Neighborhood",
      example = "Centro",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String neighborhood;

  @NotBlank(message = "City is required")
  @Schema(
      description = "City name",
      example = "Porto Alegre",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String city;

  @NotBlank(message = "State is required")
  @Pattern(regexp = "^[A-Za-z]{2}$", message = "State must contain 2 letters")
  @Schema(
      description = "State code (2 letters)",
      example = "RS",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String state;

  @NotBlank(message = "Zip code is required")
  @Pattern(regexp = "^\\d{8}$", message = "Zip code must contain 8 digits")
  @Schema(
      description = "Zip code (8 digits)",
      example = "90010120",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String zipCode;

  @Schema(description = "Latitude coordinate")
  private BigDecimal latitude;

  @Schema(description = "Longitude coordinate")
  private BigDecimal longitude;
}
