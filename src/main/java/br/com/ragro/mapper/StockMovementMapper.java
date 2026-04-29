package br.com.ragro.mapper;

import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.domain.StockMovement;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StockMovementMapper {

  @NonNull
  public static StockMovementResponse toResponse(@NonNull StockMovement movement) {
    return StockMovementResponse.builder()
        .id(movement.getId())
        .productId(movement.getProduct().getId())
        .productName(movement.getProduct().getName())
        .type(movement.getType())
        .reason(movement.getReason())
        .quantity(movement.getQuantity())
        .notes(movement.getNotes())
        .createdAt(movement.getCreatedAt())
        .currentStockQuantity(movement.getProduct().getStockQuantity())
        .build();
  }
}
