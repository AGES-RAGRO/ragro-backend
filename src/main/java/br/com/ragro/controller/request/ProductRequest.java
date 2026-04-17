package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Payload to create or update a product")
public class ProductRequest {

  @NotBlank(message = "Name is required")
  @Size(max = 150, message = "Name must contain at most 150 characters")
  @Schema(description = "Product name", example = "Organic strawberries")
  private String name;

  @Schema(description = "Product description", example = "Freshly harvested strawberries.")
  private String description;

  @NotNull(message = "Price is required")
  @DecimalMin(value = "0.01", message = "Price must be greater than zero")
  @Digits(
      integer = 8,
      fraction = 2,
      message = "Price must have at most 8 integer digits and 2 decimal places")
  @Schema(description = "Unit price", example = "18.90")
  private BigDecimal price;

  @NotBlank(message = "Unity type is required")
  @Size(max = 20, message = "Unity type must contain at most 20 characters")
  @Schema(
      description = "Product unity type",
      example = "kg",
      allowableValues = {"kg", "g", "unit", "box", "liter", "ml", "dozen"})
  private String unityType;

  @NotNull(message = "Stock quantity is required")
  @DecimalMin(value = "0.000", message = "Stock quantity must be zero or positive")
  @Digits(
      integer = 9,
      fraction = 3,
      message = "Stock quantity must have at most 9 integer digits and 3 decimal places")
  @Schema(description = "Available stock quantity", example = "35.500")
  private BigDecimal stockQuantity;

  @Schema(
      description = "Main product image URL or S3 key",
      example = "s3://bucket/products/strawberries.jpg")
  private String imageS3;

  @Schema(description = "Whether the product is active", example = "true")
  private Boolean active;

  @Schema(description = "Category identifiers assigned to the product", example = "[1, 2]")
  private List<Integer> categoryIds;

  @Valid
  @Schema(description = "Additional ordered product photos")
  private List<ProductPhotoRequest> photos;
}
