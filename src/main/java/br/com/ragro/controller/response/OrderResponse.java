package br.com.ragro.controller.response;

import br.com.ragro.domain.AddressSnapshot;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderResponse {

  private UUID id;
  private UUID customerId;
  private String customerName;

  /** Customer summary (phone, "member since"); read by the producer app from a nested {@code customer} object. */
  private OrderCustomerResponse customer;

  private UUID farmerId;
  private String farmerName;
  private AddressSnapshot deliveryAddress;
  private OrderStatus status;
  private UUID paymentMethodId;
  private PaymentStatus paymentStatus;
  private String notes;
  private BigDecimal totalAmount;
  private Boolean isNew;
  private OffsetDateTime createdAt;
  private List<OrderItemResponse> items;
  // Cancellation reason/details (persisted on Order); null when the order was not cancelled.
  private String cancellationReason;
  private String cancellationDetails;
}
