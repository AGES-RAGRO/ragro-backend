package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.RecommendationReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
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
@Schema(description = "Recommended product response")
public class RecommendationProductResponse {

  @Schema(description = "Product identifier")
  private UUID id;

  @Schema(description = "Product name", example = "Organic strawberries")
  private String name;

  @Schema(description = "Unit price", example = "18.90")
  private BigDecimal price;

  @Schema(description = "Product unity type", example = "kg")
  private String unityType;

  @Schema(description = "Main product image URL or S3 key")
  private String imageS3;

  @Schema(description = "Farmer identifier")
  private UUID farmerId;

  @Schema(description = "Farm name", example = "Sítio Boa Vista")
  private String farmName;

  @Schema(description = "Names of categories assigned to the product")
  private List<String> categoryNames;

  @Schema(description = "Recommendation score used for ranking", example = "85")
  private int score;

  @Schema(description = "Reason why the product was recommended")
  private RecommendationReason reason;
}
