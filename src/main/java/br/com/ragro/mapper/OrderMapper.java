package br.com.ragro.mapper;

import br.com.ragro.controller.response.CustomerOrderResponse;
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
   * Mapeia um pedido para a resposta do produtor (rota POST/PATCH /orders). Resolve URLs públicas
   * das fotos de produtos via {@link MinioStorageService} quando informado; aceita {@code null}
   * para chamadas legadas / testes onde a URL crua é aceitável.
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
        .items(
            order.getItems().stream()
                .map(item -> toOrderItemResponse(item, storage))
                .collect(Collectors.toList()))
        .build();
  }

  /**
   * Mapeia um pedido para a resposta do consumidor. Usado tanto pela listagem (GET
   * /orders/consumer) quanto pela tela de detalhe (GET /orders/customer/{id}). A tela de detalhe
   * consome todos os campos (itens, endereço, total, contato do produtor); a lista usa apenas id,
   * price, producerName, producerPicture e status — os demais são ignorados pelo card mas mantêm o
   * contrato do endpoint coerente.
   *
   * <p>Quando {@code storage != null}, resolve URLs públicas para foto do produtor e foto de cada
   * item.
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
    // Prioriza avatar (foto circular do perfil) sobre displayPhoto (cover/background).
    String picture = order.getFarmer().getAvatarS3();
    if (picture == null || picture.isBlank()) {
      picture = order.getFarmer().getDisplayPhotoS3();
    }
    return picture;
  }

  /**
   * Foto preferida do produto: primeira em {@link Product#getPhotos()} (ordenadas por
   * displayOrder), com fallback para {@code imageS3} legado.
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
