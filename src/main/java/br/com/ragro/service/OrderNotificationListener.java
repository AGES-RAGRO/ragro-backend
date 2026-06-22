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
      case PENDING ->
          // Criação do pedido (publicada com initiatedBy=CUSTOMER): notifica o produtor do novo
          // pedido. Antes do modelo de eventos, o OrderService chamava isto direto no
          // createOrderFromCart.
          notificationService.createProducerNewOrderNotification(event.order());
      case CONFIRMED ->
          notificationService.createCustomerOrderAcceptedNotification(event.order());
      case IN_DELIVERY ->
          notificationService.createCustomerOrderInDeliveryNotification(event.order());
      case DELIVERED ->
          notificationService.createCustomerOrderDeliveredNotification(event.order());
      case CANCELLED -> {
        // Recusa/cancelamento pelo produtor notifica o cliente; cliente cancelando o próprio
        // pedido não notifica a si mesmo, mas notifica o produtor (substitui a chamada direta que
        // o OrderService.cancelOrderAsCustomer fazia antes do modelo de eventos).
        if (event.initiatedBy() == TypeUser.FARMER) {
          notificationService.createCustomerOrderRefusedNotification(event.order());
        } else {
          notificationService.createProducerOrderCancelledByCustomerNotification(event.order());
        }
      }
    }
  }
}
