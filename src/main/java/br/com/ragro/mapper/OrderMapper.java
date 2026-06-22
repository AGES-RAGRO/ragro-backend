package br.com.ragro.mapper;

import br.com.ragro.controller.response.CustomerOrderResponse;
import br.com.ragro.controller.response.OrderCustomerResponse;
import br.com.ragro.controller.response.OrderItemResponse;
import br.com.ragro.controller.response.OrderResponse;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.OrderItem;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductPhoto;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.service.MinioStorageService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.stream.Collectors;

public class OrderMapper {

  /**
   * Maps an order to the producer-facing response (POST/PATCH /orders). Resolves public product
   * photo URLs via {@link MinioStorageService} when provided; accepts {@code null} for legacy calls
   * or tests where the raw key is acceptable.
   */
  public static OrderResponse toResponse(Order order, MinioStorageService storage) {
    if (order == null) {
      return null;
    }

    BigDecimal totalAmount =
        order.getItems().stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return OrderResponse.builder()
        .id(order.getId())
        .customerId(order.getCustomer().getId())
        .customerName(order.getCustomer().getUser().getName())
        .customer(
            OrderCustomerResponse.builder()
                .name(order.getCustomer().getUser().getName())
                .phone(order.getCustomer().getUser().getPhone())
                .memberSince(order.getCustomer().getUser().getCreatedAt())
                .build())
        .farmerId(order.getFarmer().getId())
        .farmerName(order.getFarmer().getFarmName())
        .deliveryAddress(order.getDeliveryAddressSnapshot())
        .status(order.getStatus())
        .paymentMethodId(order.getPaymentMethod().getId())
        .paymentStatus(order.getPaymentStatus())
        .notes(order.getNotes())
        .totalAmount(totalAmount)
        .isNew(order.getStatus() == OrderStatus.PENDING && !order.isSeenByFarmer())
        .createdAt(order.getCreatedAt())
        .cancellationReason(order.getCancellationReason())
        .cancellationDetails(order.getCancellationDetails())
        .items(
            order.getItems().stream()
                .map(item -> toOrderItemResponse(item, storage))
                .collect(Collectors.toList()))
        .build();
  }

  /**
   * Maps an order to the consumer-facing response, used by both the list (GET /orders/consumer) and
   * the detail screen (GET /orders/customer/{id}). The detail screen consumes every field; the list
   * only uses id, price, producerName, producerPicture and status. When {@code storage != null},
   * resolves public URLs for the producer photo and each item photo.
   */
  public static CustomerOrderResponse toCustomerOrderResponse(
      Order order, MinioStorageService storage, boolean reviewed) {
    if (order == null) {
      return null;
    }

    BigDecimal totalAmount =
        order.getItems().stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return CustomerOrderResponse.builder()
        .id(order.getId())
        .price(totalAmount)
        .totalAmount(totalAmount)
        .producerId(order.getFarmer().getId())
        .producerName(order.getFarmer().getUser().getName())
        .producerPicture(resolveUrl(storage, resolveProducerPicture(order)))
        .producerPhone(order.getFarmer().getUser().getPhone())
        .status(order.getStatus())
        .reviewed(reviewed)
        .createdAt(order.getCreatedAt())
        .deliveryAddress(order.getDeliveryAddressSnapshot())
        .cancellationReason(order.getCancellationReason())
        .cancellationDetails(order.getCancellationDetails())
        .bankInfo(PaymentMethodMapper.toBankInfo(order.getPaymentMethod()))
        // Only expose the confirmation code while the order is out for delivery.
        .confirmationCode(
            order.getStatus() == OrderStatus.IN_DELIVERY ? order.getConfirmationCode() : null)
        .actions(
            CustomerOrderResponse.Actions.builder()
                .canConfirmDelivery(order.getStatus() == OrderStatus.IN_DELIVERY)
                .canCancel(
                    order.getStatus() == OrderStatus.PENDING
                        || order.getStatus() == OrderStatus.CONFIRMED
                        || order.getStatus() == OrderStatus.IN_DELIVERY)
                .canContactProducer(
                    order.getStatus() != OrderStatus.DELIVERED
                        && order.getStatus() != OrderStatus.CANCELLED
                        && order.getFarmer().getUser().getPhone() != null
                        && !order.getFarmer().getUser().getPhone().isBlank())
                .build())
        .items(
            order.getItems().stream()
                .map(item -> toOrderItemResponse(item, storage))
                .collect(Collectors.toList()))
        .build();
  }

  private static OrderItemResponse toOrderItemResponse(
      OrderItem item, MinioStorageService storage) {
    if (item == null) {
      return null;
    }

    return OrderItemResponse.builder()
        .id(item.getId())
        .productId(item.getProduct().getId())
        .productName(item.getProductNameSnapshot())
        .productPhoto(resolveUrl(storage, resolveProductPhoto(item.getProduct())))
        .unitPrice(item.getUnitPriceSnapshot())
        .unityType(item.getUnityTypeSnapshot())
        .quantity(item.getQuantity())
        .subtotal(item.getSubtotal())
        .build();
  }

  private static String resolveProducerPicture(Order order) {
    // Prefer the avatar (circular profile photo) over displayPhoto (cover/background).
    String picture = order.getFarmer().getAvatarS3();
    if (picture == null || picture.isBlank()) {
      picture = order.getFarmer().getDisplayPhotoS3();
    }
    return picture;
  }

  /**
   * Preferred product photo: the first in {@link Product#getPhotos()} (ordered by displayOrder),
   * falling back to the legacy {@code imageS3}.
   */
  private static String resolveProductPhoto(Product product) {
    if (product == null) {
      return null;
    }
    if (product.getPhotos() != null && !product.getPhotos().isEmpty()) {
      String url =
          product.getPhotos().stream()
              .filter(p -> p != null && p.getUrl() != null && !p.getUrl().isBlank())
              .min(
                  Comparator.comparing(
                      ProductPhoto::getDisplayOrder,
                      Comparator.nullsLast(Comparator.naturalOrder())))
              .map(ProductPhoto::getUrl)
              .orElse(null);
      if (url != null) {
        return url;
      }
    }
    return product.getImageS3();
  }

  private static String resolveUrl(MinioStorageService storage, String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    if (storage == null) {
      return key;
    }
    return storage.composePublicUrl(key);
  }
}
