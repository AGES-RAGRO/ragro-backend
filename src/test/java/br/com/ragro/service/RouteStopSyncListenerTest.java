package br.com.ragro.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.ragro.domain.Order;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.domain.event.OrderStatusChangedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RouteStopSyncListenerTest {

  @Mock private DeliveryRouteService deliveryRouteService;
  @InjectMocks private RouteStopSyncListener listener;

  private OrderStatusChangedEvent event(OrderStatus newStatus) {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    return new OrderStatusChangedEvent(order, OrderStatus.IN_DELIVERY, newStatus, TypeUser.CUSTOMER);
  }

  @Test
  void shouldSyncStop_onDelivered() {
    OrderStatusChangedEvent e = event(OrderStatus.DELIVERED);
    listener.onOrderStatusChanged(e);
    verify(deliveryRouteService)
        .syncStopForTerminalOrder(e.order().getId(), OrderStatus.DELIVERED);
  }

  @Test
  void shouldSyncStop_onCancelled() {
    OrderStatusChangedEvent e = event(OrderStatus.CANCELLED);
    listener.onOrderStatusChanged(e);
    verify(deliveryRouteService)
        .syncStopForTerminalOrder(e.order().getId(), OrderStatus.CANCELLED);
  }

  @Test
  void shouldIgnore_nonTerminalTransitions() {
    listener.onOrderStatusChanged(event(OrderStatus.CONFIRMED));
    listener.onOrderStatusChanged(event(OrderStatus.IN_DELIVERY));
    listener.onOrderStatusChanged(event(OrderStatus.PENDING));
    verify(deliveryRouteService, never()).syncStopForTerminalOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldSwallowExceptions_soOrderConfirmationIsNeverUndone() {
    OrderStatusChangedEvent e = event(OrderStatus.DELIVERED);
    doThrow(new RuntimeException("db down"))
        .when(deliveryRouteService)
        .syncStopForTerminalOrder(e.order().getId(), OrderStatus.DELIVERED);

    // Must not propagate (order is already confirmed; sync is best-effort).
    listener.onOrderStatusChanged(e);
  }
}
