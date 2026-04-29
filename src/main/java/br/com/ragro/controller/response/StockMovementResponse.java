package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
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
@Schema(description = "Stock movement response")

public class StockMovementResponse {
    @Schema(description = "Movement identifier")
    private UUID id;

    @Schema(description = "Product identifier")
    private UUID productId;

    @Schema(description = "Movement type", example = "ENTRY")
    private String type;

    @Schema(description = "Movement reason", example = "MANUAL_ENTRY")
    private String reason;

    @Schema(description = "Quantity moved", example = "25.500")
    private BigDecimal quantity;

    @Schema(description = "Optional notes about the movement", example = "Incoming shipment")
    private String notes;

    @Schema(description = "Movement creation timestamp")
    private OffsetDateTime createdAt;

    @Schema(description = "Updated product stock quantity after this movement", example = "150.500")
    private BigDecimal updatedProductStock;
}
