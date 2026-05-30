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

  // Campos adicionais consumidos pela tela de detalhe (GET /orders/customer/{id}).
  // A lista (GET /orders/consumer) preenche apenas os campos acima — os abaixo
  // são populados somente pelo mapper de detalhe e podem vir nulos na listagem.
  private UUID producerId;
  private String producerPhone;
  private BigDecimal totalAmount;
  private OffsetDateTime createdAt;
  private AddressSnapshot deliveryAddress;
  private List<OrderItemResponse> items;
  private String cancellationReason;
  private String cancellationDetails;
  private BankInfoResponse bankInfo;
  private OrderActionsResponse actions;
}
