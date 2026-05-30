package br.com.ragro.controller;

import br.com.ragro.controller.request.RouteRequestDTO;
import br.com.ragro.controller.response.RouteResponseDTO;
import br.com.ragro.service.GoogleMapsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
@Tag(name = "Routes", description = "Rota optimization and geocoding operations via Google Maps")
public class RouteController {

  private final GoogleMapsService googleMapsService;

  @PostMapping("/optimize")
  @Operation(
      summary = "Optimize route",
      description =
          "Calculates the most efficient route passing through all waypoints and returns the distance, duration, and encoded polyline for the map.")
  public ResponseEntity<RouteResponseDTO> optimizeRoute(
      @Valid @RequestBody RouteRequestDTO request) {
    return ResponseEntity.ok(googleMapsService.optimizeRoute(request));
  }
}
