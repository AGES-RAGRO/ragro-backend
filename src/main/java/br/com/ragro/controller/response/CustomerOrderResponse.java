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

  /**
   * Dados bancários/PIX do produtor para o pagamento, espelhando o que o CartResponse entrega no
   * checkout. Sem isto o card "PAGAMENTO" sumia ao reabrir o pedido (auditoria Fase 0, achado A7).
   */
  private BankInfoResponse bankInfo;

  // 4-digit delivery confirmation code shown to the consumer when status is IN_DELIVERY.
  // Null for all other statuses.
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
