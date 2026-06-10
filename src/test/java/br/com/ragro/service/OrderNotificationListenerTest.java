package br.com.ragro.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.ragro.domain.Order;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.domain.event.OrderStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderNotificationListenerTest {

  @Mock private NotificationService notificationService;

  @InjectMocks private OrderNotificationListener listener;

  private Order order;

  @BeforeEach
  void setUp() {
    order = new Order();
  }

  @Test
  void shouldNotifyAccepted_whenOrderConfirmed() {
    listener.onOrderStatusChanged(
        new OrderStatusChangedEvent(
            order, OrderStatus.PENDING, OrderStatus.CONFIRMED, TypeUser.FARMER));

    verify(notificationService).createCustomerOrderAcceptedNotification(order);
  }

  @Test
  void shouldNotifyInDelivery_whenOrderInDelivery() {
    listener.onOrderStatusChanged(
        new OrderStatusChangedEvent(
            order, OrderStatus.CONFIRMED, OrderStatus.IN_DELIVERY, TypeUser.FARMER));

    verify(notificationService).createCustomerOrderInDeliveryNotification(order);
  }

  @Test
  void shouldNotifyDelivered_whenOrderDelivered() {
    listener.onOrderStatusChanged(
        new OrderStatusChangedEvent(
            order, OrderStatus.IN_DELIVERY, OrderStatus.DELIVERED, TypeUser.CUSTOMER));

    verify(notificationService).createCustomerOrderDeliveredNotification(order);
  }

  @Test
  void shouldNotifyRefused_whenFarmerCancels() {
    listener.onOrderStatusChanged(
        new OrderStatusChangedEvent(
            order, OrderStatus.PENDING, OrderStatus.CANCELLED, TypeUser.FARMER));

    verify(notificationService).createCustomerOrderRefusedNotification(order);
  }

  @Test
  void shouldNotNotify_whenCustomerCancelsOwnOrder() {
    listener.onOrderStatusChanged(
        new OrderStatusChangedEvent(
            order, OrderStatus.PENDING, OrderStatus.CANCELLED, TypeUser.CUSTOMER));

    verifyNoInteractions(notificationService);
  }

  @Test
  void shouldNotNotify_whenOrderCreated() {
    listener.onOrderStatusChanged(
        new OrderStatusChangedEvent(order, null, OrderStatus.PENDING, TypeUser.CUSTOMER));

    verifyNoInteractions(notificationService);
  }
}
