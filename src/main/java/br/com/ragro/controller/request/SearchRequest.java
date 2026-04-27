package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Marketplace search query")
public class SearchRequest {

  @NotBlank(message = "query is required")
  @Size(max = 120, message = "query must contain at most 120 characters")
  @Schema(
      description = "Search term used to find marketplace products and producers",
      example = "tomate")
  private String query;

  @Size(max = 80, message = "category must contain at most 80 characters")
  @Schema(
      description = "Optional product category filter",
      example = "Frutas",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private String category;
}
