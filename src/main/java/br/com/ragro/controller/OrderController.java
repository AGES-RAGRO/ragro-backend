package br.com.ragro.controller;

import br.com.ragro.controller.request.UpdateOrderStatusRequest;
import br.com.ragro.controller.response.CustomerOrderResponse;
import br.com.ragro.controller.response.OrderResponse;
import br.com.ragro.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Endpoints for managing orders")
public class OrderController {

  private final OrderService orderService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create an order",
      description = "Creates a new order from the active cart. Automatically selects the primary delivery address and the farmer's default payment method if not provided."
  )
  public OrderResponse createOrder(@AuthenticationPrincipal Jwt jwt) {
    return orderService.createOrderFromCart(jwt);
  }

  @GetMapping("/customer/{id}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Get my order by id",
      description = "Returns one order by id for the authenticated customer.")
  public CustomerOrderResponse getMyOrderById(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return orderService.getMyOrderById(id, jwt);
  }

  @PatchMapping("/{id}/status")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Update order status",
      description = "Allows a producer to update the status of an owned order.")
  public OrderResponse updateOrderStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateOrderStatusRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return orderService.updateOrderStatus(id, request.getStatus(), jwt);
  }

  @PatchMapping("/{id}/cancel")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Cancel an order",
      description = "Cancels an order that is still in PENDING status. Only the customer who created the order can cancel it."
  )
  public OrderResponse cancelOrder(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return orderService.cancelOrder(id, jwt);
  }
}
