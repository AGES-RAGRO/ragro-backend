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

  // Detail-only fields (GET /orders/customer/{id}); null in the GET /orders/consumer listing.
  private UUID producerId;
  private String producerPhone;
  private BigDecimal totalAmount;
  private OffsetDateTime createdAt;
  private AddressSnapshot deliveryAddress;
  private List<OrderItemResponse> items;
  // Cancellation reason/details (persisted on Order); null when the order was not cancelled.
  private String cancellationReason;
  private String cancellationDetails;

  /** Producer's bank/PIX details (mirrors CartResponse at checkout); needed for the "PAGAMENTO" card. */
  private BankInfoResponse bankInfo;

  // 4-digit delivery confirmation code; only set when status is IN_DELIVERY, else null.
  private String confirmationCode;

  // Actions the consumer can take on this order.
  private Actions actions;

  @Data
  @Builder
  public static class Actions {
    private boolean canConfirmDelivery;
    private boolean canCancel;
    private boolean canContactProducer;
  }
}
