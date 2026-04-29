package br.com.ragro.controller.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemResponse {

  private UUID id;
  private UUID productId;
  private String productName;
  private BigDecimal unitPrice;
  private String unityType;
  private BigDecimal quantity;
  private BigDecimal subtotal;

}
