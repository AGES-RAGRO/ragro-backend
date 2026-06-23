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
 * Syncs the route stop when the order becomes terminal outside the stop flow — mainly the customer
 * confirming delivery ({@code POST /orders/customer/{id}/confirm-delivery}), which left the stop PENDING
 * (ghost route).
 *
 * <p>Separate bean from {@link DeliveryRouteService} on purpose: the sync method is
 * {@code @Transactional(REQUIRES_NEW)} and must go through the Spring proxy (self-invocation would void
 * propagation). {@code AFTER_COMMIT} ensures the order was committed first; the {@code try/catch} makes
 * the sync best-effort so a failure here neither undoes the confirmation nor surfaces to the user.
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
