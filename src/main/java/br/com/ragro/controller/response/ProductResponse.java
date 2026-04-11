package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
@Schema(description = "Product response")
public class ProductResponse {

  @Schema(description = "Product identifier")
  private UUID id;

  @Schema(description = "Farmer identifier")
  private UUID farmerId;

  @Schema(description = "Product name", example = "Organic strawberries")
  private String name;

  @Schema(description = "Product description")
  private String description;

  @Schema(description = "Unit price", example = "18.90")
  private BigDecimal price;

  @Schema(description = "Product unity type", example = "kg")
  private String unityType;

  @Schema(description = "Available stock quantity", example = "35.500")
  private BigDecimal stockQuantity;

  @Schema(description = "Main product image URL or S3 key")
  private String imageS3;

  @Schema(description = "Whether the product is active", example = "true")
  private boolean active;

  @Schema(description = "Creation timestamp")
  private OffsetDateTime createdAt;

  @Schema(description = "Last update timestamp")
  private OffsetDateTime updatedAt;

  @Schema(description = "Assigned categories")
  private List<ProductCategoryResponse> categories;

  @Schema(description = "Additional ordered photos")
  private List<ProductPhotoResponse> photos;
}
