package br.com.ragro.controller.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartItemResponse {
  private UUID id;
  private UUID productId;
  private String productName;
  private BigDecimal priceSnapshot; 
  private String imageS3;
  private BigDecimal quantity;
  private BigDecimal subtotal;
}