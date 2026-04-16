package br.com.ragro.controller.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MarketplaceProducerResponse {

  @Schema(description = "Unique identifier of the producer")
  private UUID id;

  @Schema(description = "Full name of the owner", example = "João Silva")
  private String ownerName;

  @Schema(description = "Farm name", example = "Fazenda Sol Nascente")
  private String farmName;

  @Schema(description = "Description of the farm and products", example = "Organic vegetables...")
  private String description;

  @Schema(description = "Avatar URL or S3 key", example = "producers/avatar.png")
  private String avatarS3;

  @Schema(description = "Cover/background photo URL or S3 key", example = "producers/cover.png")
  private String displayPhotoS3;

  @Schema(description = "Average rating of the producer", example = "4.95")
  private BigDecimal averageRating;
}
