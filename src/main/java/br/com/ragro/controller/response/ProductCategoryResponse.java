package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Product category response")
public class ProductCategoryResponse {

  @Schema(description = "Category identifier", example = "1")
  private Integer id;

  @Schema(description = "Category name", example = "Fruits")
  private String name;

  @Schema(description = "Category description", example = "Fresh fruit products")
  private String description;
}
