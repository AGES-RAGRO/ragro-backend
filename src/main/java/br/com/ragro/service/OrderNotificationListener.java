package br.com.ragro.service;

import br.com.ragro.domain.event.OrderStatusChangedEvent;
import br.com.ragro.domain.enums.TypeUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Traduz transições de status de pedido em notificações ao cliente. Listener SÍNCRONO e na mesma
 * transação da transição (mesmo comportamento de quando o {@code OrderService} chamava o {@code
 * NotificationService} direto): a notificação é persistida junto com o pedido ou nada é salvo.
 * Na etapa futura de mensageria, este listener vira um consumer externo sem tocar no OrderService.
 */
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

  private final NotificationService notificationService;

  @EventListener
  public void onOrderStatusChanged(OrderStatusChangedEvent event) {
    switch (event.newStatus()) {
      case CONFIRMED ->
          notificationService.createCustomerOrderAcceptedNotification(event.order());
      case IN_DELIVERY ->
          notificationService.createCustomerOrderInDeliveryNotification(event.order());
      case DELIVERED ->
          notificationService.createCustomerOrderDeliveredNotification(event.order());
      case CANCELLED -> {
        // Cliente cancelando o próprio pedido não gera notificação para ele mesmo;
        // recusa/cancelamento pelo produtor notifica o cliente.
        if (event.initiatedBy() == TypeUser.FARMER) {
          notificationService.createCustomerOrderRefusedNotification(event.order());
        }
      }
      default -> {
        // PENDING (criação) não notifica.
      }
    }
  }
}
