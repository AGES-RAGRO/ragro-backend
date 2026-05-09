package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload to request product recommendations")
public class RecommendationRequest {

  @Min(value = 1, message = "Limit must be at least 1")
  @Max(value = 100, message = "Limit must be at most 100")
  @Schema(description = "Maximum number of recommendations to return", example = "20")
  private int limit = 20;

  @Schema(description = "Product identifiers to exclude from the recommendation list")
  private List<UUID> excludeProductIds;

  @Schema(description = "Optional category filter", example = "Hortaliças")
  private String category;
}
