package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.OrderStatus;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerOrderResponse {

  private BigDecimal price;
  private String producerName;
  private String producerPicture;
  private OrderStatus status;
}
