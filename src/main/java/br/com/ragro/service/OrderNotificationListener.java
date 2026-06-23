package br.com.ragro.service;

import br.com.ragro.domain.event.OrderStatusChangedEvent;
import br.com.ragro.domain.enums.TypeUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Translates order status transitions into notifications. SYNCHRONOUS listener in the same
 * transaction as the transition: the notification is persisted with the order or nothing is saved.
 */
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

  private final NotificationService notificationService;

  @EventListener
  public void onOrderStatusChanged(OrderStatusChangedEvent event) {
    switch (event.newStatus()) {
      case PENDING ->
          // Order creation (initiatedBy=CUSTOMER): notify the producer of the new order.
          notificationService.createProducerNewOrderNotification(event.order());
      case CONFIRMED ->
          notificationService.createCustomerOrderAcceptedNotification(event.order());
      case IN_DELIVERY ->
          notificationService.createCustomerOrderInDeliveryNotification(event.order());
      case DELIVERED ->
          notificationService.createCustomerOrderDeliveredNotification(event.order());
      case CANCELLED -> {
        // Producer rejection/cancellation notifies the customer; a customer cancelling their own
        // order notifies the producer, not themselves.
        if (event.initiatedBy() == TypeUser.FARMER) {
          notificationService.createCustomerOrderRefusedNotification(event.order());
        } else {
          notificationService.createProducerOrderCancelledByCustomerNotification(event.order());
        }
      }
    }
  }
}
