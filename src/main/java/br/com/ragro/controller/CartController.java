package br.com.ragro.controller;

import br.com.ragro.controller.request.AddToCartRequest;
import br.com.ragro.controller.request.UpdateCartItemRequest;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
  
  @DeleteMapping("/items/{id}")
  @Operation(
          summary = "Remove item from cart",
          description = "Removes an item from the authenticated consumer's active cart. If it is the last item, the cart is deactivated.")
  public ResponseEntity<CartResponse> removeItem(
          @PathVariable UUID id,
          @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(cartService.removeItem(jwt, id));
  }


  @PatchMapping("/items/{id}")
  @Operation(
      summary = "Update cart item quantity",
      description = "Replaces the quantity of the given cart item. Validates stock availability.")
  public ResponseEntity<CartResponse> updateItemQuantity(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCartItemRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(cartService.updateItemQuantity(jwt, id, request));
  }

  @DeleteMapping
  @Operation(
      summary = "Clear entire cart",
      description = "Removes all items and deactivates the current active cart.")
  public ResponseEntity<CartResponse> clearCart(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(cartService.clearActiveCart(jwt));
  }
}

