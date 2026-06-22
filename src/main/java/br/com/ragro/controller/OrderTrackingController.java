package br.com.ragro.controller;

import br.com.ragro.controller.response.OrderTrackingResponse;
import br.com.ragro.domain.User;
import br.com.ragro.service.TrackingService;
import br.com.ragro.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rastreamento de pedido para o cliente: estado inicial da tela de acompanhamento e fallback de
 * polling quando o WebSocket cai (degradação graciosa). O stream ao vivo é o tópico STOMP {@code
 * /topic/routes/{routeId}} (routeId vem desta resposta).
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Endpoints for managing orders")
public class OrderTrackingController {

  private final TrackingService trackingService;
  private final UserService userService;

  @GetMapping("/{id}/tracking")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Track order delivery",
      description =
          "Returns the producer's last known position and dynamic ETA for the customer's own"
              + " order while it is on an active route. available=false when not being delivered."
              + " Privacy: only this order's data is exposed (other stops are just a count).")
  public ResponseEntity<OrderTrackingResponse> trackOrder(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    return ResponseEntity.ok(trackingService.trackingForOrder(id, user));
  }
}
