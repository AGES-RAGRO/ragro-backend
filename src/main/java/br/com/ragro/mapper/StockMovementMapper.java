package br.com.ragro.mapper;

import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.domain.StockMovement;

public final class StockMovementMapper {

  private StockMovementMapper() {}

  public static StockMovementResponse toResponse(StockMovement stockMovement) {
    return StockMovementResponse.builder()
        .id(stockMovement.getId())
        .productId(stockMovement.getProduct().getId())
        .type(stockMovement.getType())
        .reason(stockMovement.getReason())
        .quantity(stockMovement.getQuantity())
        .notes(stockMovement.getNotes())
        .createdAt(stockMovement.getCreatedAt())
        .build();
  }
}