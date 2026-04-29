package br.com.ragro.mapper;

import br.com.ragro.controller.response.OrderItemResponse;
import br.com.ragro.controller.response.OrderResponse;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.OrderItem;
import java.math.BigDecimal;
import java.util.stream.Collectors;

public class OrderMapper {

  public static OrderResponse toResponse(Order order) {
    if (order == null) {
      return null;
    }

    BigDecimal totalAmount = order.getItems().stream()
        .map(OrderItem::getSubtotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    return OrderResponse.builder()
        .id(order.getId())
        .customerId(order.getCustomer().getId())
        .customerName(order.getCustomer().getUser().getName())
        .farmerId(order.getFarmer().getId())
        .farmerName(order.getFarmer().getFarmName())
        .deliveryAddress(order.getDeliveryAddressSnapshot())
        .status(order.getStatus())
        .paymentMethodId(order.getPaymentMethod().getId())
        .paymentStatus(order.getPaymentStatus())
        .notes(order.getNotes())
        .totalAmount(totalAmount)
        .createdAt(order.getCreatedAt())
        .items(order.getItems().stream()
            .map(OrderMapper::toOrderItemResponse)
            .collect(Collectors.toList()))
        .build();
  }

  private static OrderItemResponse toOrderItemResponse(OrderItem item) {
    if (item == null) {
      return null;
    }

    return OrderItemResponse.builder()
        .id(item.getId())
        .productId(item.getProduct().getId())
        .productName(item.getProductNameSnapshot())
        .unitPrice(item.getUnitPriceSnapshot())
        .unityType(item.getUnityTypeSnapshot())
        .quantity(item.getQuantity())
        .subtotal(item.getSubtotal())
        .build();
  }
}
