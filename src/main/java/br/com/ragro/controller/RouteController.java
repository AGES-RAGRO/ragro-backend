package br.com.ragro.controller;

import br.com.ragro.controller.request.AddStopsRequest;
import br.com.ragro.controller.request.CreateRouteRequest;
import br.com.ragro.controller.request.UpdateRouteStopRequest;
import br.com.ragro.controller.response.RouteResponse;
import br.com.ragro.service.DeliveryRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Persisted delivery routes derived from the producer's ORDERS, tracked per stop without
 * recalculating on Google per delivery (replaces the old ephemeral {@code POST /routes/optimize}).
 */
@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
@Tag(name = "Routes", description = "Persisted delivery routes (Google Routes API)")
public class RouteController {

  private final DeliveryRouteService deliveryRouteService;

  @PostMapping
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Create delivery route",
      description =
          "Builds the optimized round-trip route from the producer's CONFIRMED/IN_DELIVERY"
              + " orders (single Google call), persists ordered stops with per-leg ETA, replaces"
              + " any previous active route and moves CONFIRMED orders to IN_DELIVERY.")
  public ResponseEntity<RouteResponse> createRoute(
      @Valid @RequestBody CreateRouteRequest request, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(deliveryRouteService.createRoute(request, jwt));
  }

  @GetMapping("/active")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Get active route",
      description = "Returns the producer's active route (resume after app restart). 404 if none.")
  public ResponseEntity<RouteResponse> getActiveRoute(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(deliveryRouteService.getActiveRoute(jwt));
  }

  @PatchMapping("/{routeId}/add-stops")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Add new orders to the active route",
      description =
          "Pulls newly CONFIRMED orders into the ACTIVE route, keeping DELIVERED/FAILED stops as"
              + " history and re-optimizing only the pending portion (single Google call). New orders"
              + " move to IN_DELIVERY. When the producer's current GPS is sent, the remaining route is"
              + " re-anchored/re-optimized from where they are now. Idempotent: with no new order it"
              + " self-heals and returns the route unchanged (no re-anchor). 404 if already completed.")
  public ResponseEntity<RouteResponse> addStops(
      @PathVariable UUID routeId,
      @Valid @RequestBody(required = false) AddStopsRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(deliveryRouteService.addStops(routeId, request, jwt));
  }

  @PatchMapping("/{routeId}/stops/{stopId}")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Update route stop status",
      description =
          "Marks a stop as ARRIVED, DELIVERED or FAILED. DELIVERED completes the order and EXIGE o"
              + " código de confirmação de 4 dígitos do consumidor (mesma validação/lockout do"
              + " /confirm-delivery-with-code). Completing every stop closes the route.")
  public ResponseEntity<RouteResponse> updateStop(
      @PathVariable UUID routeId,
      @PathVariable UUID stopId,
      @Valid @RequestBody UpdateRouteStopRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(
        deliveryRouteService.updateStop(
            routeId, stopId, request.getStatus(), request.getCode(), jwt));
  }
}
