package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
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
@Schema(description = "Product photo response")
public class ProductPhotoResponse {

  @Schema(description = "Photo identifier")
  private UUID id;

  @Schema(description = "Photo URL")
  private String url;

  @Schema(description = "Display order", example = "0")
  private Short displayOrder;

  @Schema(description = "Creation timestamp")
  private OffsetDateTime createdAt;
}
