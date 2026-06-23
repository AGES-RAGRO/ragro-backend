package br.com.ragro.domain.event;

import br.com.ragro.domain.Order;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.TypeUser;

/**
 * Domain event published on every order status transition via Spring {@code ApplicationEventPublisher}.
 * Consumed synchronously in-transaction by {@code OrderNotificationListener}.
 *
 * @param order order already saved with the new status
 * @param previousStatus previous status ({@code null} on order creation)
 * @param newStatus applied status
 * @param initiatedBy role that triggered the transition — e.g. decides whether the customer is
 *     notified of a cancellation (producer rejected) or not (self-cancelled)
 */
public record OrderStatusChangedEvent(
    Order order, OrderStatus previousStatus, OrderStatus newStatus, TypeUser initiatedBy) {}
