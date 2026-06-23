package br.com.ragro.domain.enums;

public enum NotificationType {
  // Customer-facing (order status changes)
  ORDER_CONFIRMED,
  ORDER_IN_DELIVERY,
  ORDER_DELIVERED,
  ORDER_REFUSED,
  // Producer-facing
  NEW_ORDER,
  ORDER_CANCELLED_BY_CUSTOMER,
  LOW_STOCK
}
