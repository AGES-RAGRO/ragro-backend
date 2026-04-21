package br.com.ragro.controller;

import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal Jwt jwt) {
        return cartService.getCart(jwt);
    }
}