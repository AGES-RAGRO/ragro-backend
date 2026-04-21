package br.com.ragro.service;

import br.com.ragro.controller.response.CartItemResponse;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.repository.CartRepository;
import br.com.ragro.domain.Cart;
import br.com.ragro.domain.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;

    public CartResponse getCart(Jwt jwt) {
        UUID customerId = UUID.fromString(jwt.getSubject());

        Cart cart = cartRepository.findByCustomerIdAndActiveTrue(customerId)
            .orElseThrow(() -> new RuntimeException("Carrinho ativo não encontrado"));

        BigDecimal totalAmount = cart.getItems().stream()
            .map(item -> item.getPriceSnapshot().multiply(item.getQuantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
            .id(cart.getId())
            .farmerId(cart.getFarmer().getId())
            .farmName(cart.getFarmer().getFarmName())
            .totalAmount(totalAmount)
            .items(cart.getItems().stream().map(item ->
                CartItemResponse.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .priceSnapshot(item.getPriceSnapshot())
                    .quantity(item.getQuantity())
                    .subtotal(item.getPriceSnapshot().multiply(item.getQuantity()))
                    .imageS3(item.getProduct().getImageS3())
                    .build()
            ).collect(Collectors.toList()))
            .build();
    }
}