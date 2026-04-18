package br.com.ragro.controller;

import br.com.ragro.controller.request.AddToCartRequest;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers/carts")
@RequiredArgsConstructor
@Tag(name = "Carts", description = "Cart operations for consumers")
public class CartController {

  private final CartService cartService;

  @PostMapping("/items")
  @Operation(
      summary = "Add or update item in cart",
      description = "Automatically creates a cart if none exists. Blocks if adding items from multiple farmers.")
  public ResponseEntity<CartResponse> addItem(
      @Valid @RequestBody AddToCartRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(cartService.addItem(jwt, request));
  }

  @GetMapping
  @Operation(
      summary = "Get current cart",
      description = "Returns the active cart for the authenticated consumer.")
  public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(cartService.getCart(jwt));
  }
}
