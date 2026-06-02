package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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
@Schema(description = "List of recommended products with metadata")
public class RecommendationResponse {

  @Schema(description = "Recommended products")
  private List<RecommendationProductResponse> recommendations;

  @Schema(description = "Total number of recommendations returned", example = "20")
  private int total;
}
