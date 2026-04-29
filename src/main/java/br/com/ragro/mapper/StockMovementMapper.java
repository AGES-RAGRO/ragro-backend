package br.com.ragro.mapper;

import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.domain.StockMovement;
import java.math.BigDecimal;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StockMovementMapper {

    public static StockMovementResponse toResponse(
      StockMovement movement, BigDecimal updatedProductStock) {
    return StockMovementResponse.builder()
        .id(movement.getId())
        .productId(movement.getProduct().getId())
        .type(movement.getType().name())
        .reason(movement.getReason().name())
        .quantity(movement.getQuantity())
        .notes(movement.getNotes())
        .createdAt(movement.getCreatedAt())
        .updatedProductStock(updatedProductStock)
        .build();
  }
}
