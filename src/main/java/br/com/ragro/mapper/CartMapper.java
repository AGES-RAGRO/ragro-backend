package br.com.ragro.mapper;

import br.com.ragro.controller.response.CartItemResponse;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.domain.Cart;
import br.com.ragro.domain.CartItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class CartMapper {

  public static CartResponse toResponse(Cart cart) {
    List<CartItemResponse> itemResponses = cart.getItems().stream()
        .filter(CartItem::isActive)
        .map(CartMapper::toItemResponse)
        .collect(Collectors.toList());

    BigDecimal total = itemResponses.stream()
        .map(CartItemResponse::getSubtotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    return CartResponse.builder()
        .id(cart.getId())
        .farmerId(cart.getFarmer().getId())
        .farmName(cart.getFarmer().getFarmName())
        .items(itemResponses)
        .totalAmount(total)
        .build();
  }

  public static CartItemResponse toItemResponse(CartItem item) {
    BigDecimal subtotal = item.getProduct().getPrice().multiply(item.getQuantity());

    return CartItemResponse.builder()
        .id(item.getId())
        .productId(item.getProduct().getId())
        .productName(item.getProduct().getName())
        .unitPrice(item.getProduct().getPrice())
        .unityType(item.getProduct().getUnityType())
        .imageS3(item.getProduct().getImageS3())
        .quantity(item.getQuantity())
        .subtotal(subtotal)
        .build();
  }
}
