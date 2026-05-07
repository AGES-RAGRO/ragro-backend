package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
@Schema(description = "Address information")
public class AddressResponse {

  @Schema(
      description = "Unique identifier of the address",
      example = "550e8400-e29b-41d4-a716-446655440001")
  private UUID id;

  @Schema(
      description = "Street name",
      example = "Rua das Flores",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String street;

  @Schema(
      description = "Street number",
      example = "123",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String number;

  @Schema(description = "Complementary information", example = "Apto 42")
  private String complement;

  @Schema(
      description = "Neighborhood name",
      example = "Centro",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String neighborhood;

  @Schema(
      description = "City name",
      example = "Porto Alegre",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String city;

  @Schema(
      description = "State code",
      example = "RS",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String state;

  @Schema(
      description = "Zip code",
      example = "90010120",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String zipCode;

  @Schema(description = "Latitude coordinate")
  private BigDecimal latitude;

  @Schema(description = "Longitude coordinate")
  private BigDecimal longitude;

  @Schema(description = "Whether this is the primary address", example = "true")
  private boolean isPrimary;

  @Schema(description = "Address creation timestamp")
  private OffsetDateTime createdAt;
}
