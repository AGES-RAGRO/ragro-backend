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
@Schema(description = "Unified marketplace search result")
public class SearchResultResponse {

  @Schema(description = "Identifier of the matched entity")
  private UUID id;

  @Schema(description = "Type of search result", example = "product")
  private String type;

  @Schema(description = "Main title displayed in the search result")
  private String name;

  @Schema(description = "Secondary text displayed in the search result")
  private String subtitle;

  @Schema(description = "Main result image URL or S3 key")
  private String imageUrl;

  @Schema(description = "Product price when the result is a product", example = "12.90")
  private BigDecimal price;

  @Schema(description = "Average producer rating when the result is a producer", example = "4.80")
  private BigDecimal rating;

  @Schema(description = "Producer review count when the result is a producer", example = "42")
  private Integer reviewCount;

  @Schema(description = "Product category when the result is a product", example = "Frutas")
  private String category;

  @Schema(description = "Distance placeholder for future integrations", example = "1.2")
  private Double distance;

  @Schema(description = "Product unit when the result is a product", example = "kg")
  private String unit;
}
