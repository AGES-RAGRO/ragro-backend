package br.com.ragro.controller.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartResponse {
  private UUID id;
  private UUID farmerId;
  private String farmName;
  private List<CartItemResponse> items;
  private BigDecimal totalAmount;
}
