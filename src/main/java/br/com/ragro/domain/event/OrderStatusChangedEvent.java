package br.com.ragro.domain.event;

import br.com.ragro.domain.Order;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.TypeUser;

/**
 * Evento de domínio publicado a cada transição de status de pedido (in-process, via Spring
 * {@code ApplicationEventPublisher}). Hoje é consumido sincronicamente na mesma transação pelo
 * {@code OrderNotificationListener}; é também o ponto de extração natural para a futura etapa de
 * mensageria (basta trocar o listener por um publisher externo) e o gancho que o rastreamento em
 * tempo real (Fase 3) assinará.
 *
 * @param order pedido já salvo com o novo status
 * @param previousStatus status anterior ({@code null} na criação do pedido)
 * @param newStatus status aplicado
 * @param initiatedBy papel de quem disparou a transição — decide, por exemplo, se o cliente é
 *     notificado de um cancelamento (produtor recusou) ou não (ele mesmo cancelou)
 */
public record OrderStatusChangedEvent(
    Order order, OrderStatus previousStatus, OrderStatus newStatus, TypeUser initiatedBy) {}
