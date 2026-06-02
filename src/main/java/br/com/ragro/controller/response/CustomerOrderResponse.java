package br.com.ragro.controller.response;

import br.com.ragro.domain.AddressSnapshot;
import br.com.ragro.domain.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerOrderResponse {

  private UUID id;
  private BigDecimal price;
  private String producerName;
  private String producerPicture;
  private OrderStatus status;
  private boolean reviewed;

  // Detail-only fields (GET /orders/customer/{id}). The list (GET /orders/consumer) fills only the
  // fields above; these are populated only by the detail mapper and may be null in the listing.
  private UUID producerId;
  private String producerPhone;
  private BigDecimal totalAmount;
  private OffsetDateTime createdAt;
  private AddressSnapshot deliveryAddress;
  private List<OrderItemResponse> items;
  // Cancellation reason/details (persisted on Order); null when the order was not cancelled.
  private String cancellationReason;
  private String cancellationDetails;
}
