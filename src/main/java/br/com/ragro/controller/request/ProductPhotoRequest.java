package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Product photo payload")
public class ProductPhotoRequest {

  @NotBlank(message = "Photo URL is required")
  @Schema(description = "Photo URL or S3 object URL", example = "s3://bucket/products/apple-1.jpg")
  private String url;

  @NotNull(message = "Display order is required")
  @PositiveOrZero(message = "Display order must be zero or positive")
  @Schema(description = "Photo ordering position", example = "0")
  private Short displayOrder;
}
