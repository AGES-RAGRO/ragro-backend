package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
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
@Schema(description = "Producer location data")
public class LocationResponse {

  @Schema(description = "Producer ID")
  private UUID id;

  @Schema(description = "Farm Name", example = "Fazenda Feliz")
  private String farmName;

  @Schema(description = "Latitude", example = "-23.550520")
  private BigDecimal latitude;

  @Schema(description = "Longitude", example = "-46.633308")
  private BigDecimal longitude;

  @Schema(description = "Profile Picture URL", example = "http://localhost:9000/ragro/avatar.jpg")
  private String avatarUrl;
}
