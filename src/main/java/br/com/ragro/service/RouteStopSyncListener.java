package br.com.ragro.service;

import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mantém a parada da rota em sincronia quando o pedido vira terminal por fora do fluxo de parada —
 * o caso real é o cliente confirmando a entrega ({@code POST /orders/customer/{id}/confirm-delivery}),
 * que mexe só no pedido e deixava a parada PENDING (rota nunca fechava → "rota fantasma").
 *
 * <p>Bean SEPARADO do {@link DeliveryRouteService} de propósito: o método de sync é
 * {@code @Transactional(REQUIRES_NEW)} e precisa passar pelo proxy do Spring — chamá-lo de dentro
 * do próprio serviço (self-invocation) anularia a propagação. {@code AFTER_COMMIT} garante que o
 * pedido já foi confirmado/cancelado antes de tocar na rota; o {@code try/catch} torna o sync
 * best-effort: uma falha aqui não pode desfazer a confirmação nem propagar erro ao usuário.
 */
@Component
@RequiredArgsConstructor
public class RouteStopSyncListener {

  private static final Logger log = LoggerFactory.getLogger(RouteStopSyncListener.class);

  private final DeliveryRouteService deliveryRouteService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOrderStatusChanged(OrderStatusChangedEvent event) {
    OrderStatus status = event.newStatus();
    if (status != OrderStatus.DELIVERED && status != OrderStatus.CANCELLED) {
      return;
    }
    try {
      deliveryRouteService.syncStopForTerminalOrder(event.order().getId(), status);
    } catch (RuntimeException e) {
      log.warn(
          "Falha ao sincronizar a parada da rota para o pedido {} ({}): {}",
          event.order().getId(),
          status,
          e.getMessage());
    }
  }
}
