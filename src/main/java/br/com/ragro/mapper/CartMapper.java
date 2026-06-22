package br.com.ragro.mapper;

import br.com.ragro.controller.response.CartItemResponse;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.domain.Cart;
import br.com.ragro.domain.CartItem;
import br.com.ragro.domain.PaymentMethod;
import br.com.ragro.service.MinioStorageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class CartMapper {

  public static CartResponse toResponse(Cart cart) {
    return toResponse(cart, null, null);
  }

  public static CartResponse toResponse(
      Cart cart, PaymentMethod paymentMethod, MinioStorageService storage) {
    List<CartItemResponse> itemResponses =
        cart.getItems().stream()
            .filter(CartItem::isActive)
            .map(item -> toItemResponse(item, storage))
            .collect(Collectors.toList());

    BigDecimal total =
        itemResponses.stream()
            .map(CartItemResponse::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return CartResponse.builder()
        .id(cart.getId())
        .farmerId(cart.getFarmer().getId())
        .farmName(cart.getFarmer().getFarmName())
        .items(itemResponses)
        .totalAmount(total)
        .bankInfo(PaymentMethodMapper.toBankInfo(paymentMethod))
        .build();
  }

  public static CartItemResponse toItemResponse(CartItem item, MinioStorageService storage) {
    BigDecimal subtotal = item.getProduct().getPrice().multiply(item.getQuantity());

    String imageUrl =
        storage != null
            ? storage.composePublicUrl(item.getProduct().getImageS3())
            : item.getProduct().getImageS3();

    return CartItemResponse.builder()
        .id(item.getId())
        .productId(item.getProduct().getId())
        .productName(item.getProduct().getName())
        .unitPrice(item.getProduct().getPrice())
        .unityType(item.getProduct().getUnityType())
        .imageS3(imageUrl)
        .quantity(item.getQuantity())
        .subtotal(subtotal)
        .build();
  }

}
