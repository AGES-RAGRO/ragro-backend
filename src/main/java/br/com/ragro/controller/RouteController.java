package br.com.ragro.controller;

import br.com.ragro.controller.response.BatchGeocodeResponse;
import br.com.ragro.controller.response.DeliveryRouteResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.service.GeocodingService;
import br.com.ragro.service.RouteService;
import br.com.ragro.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Route", description = "Delivery route operations")
public class RouteController {

  private final RouteService routeService;
  private final GeocodingService geocodingService;
  private final AddressRepository addressRepository;
  private final UserService userService;

  @GetMapping("/routes/today")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Get today's optimized delivery route",
      description = "Returns the optimal delivery route for in-delivery orders of the current day.")
  public ResponseEntity<DeliveryRouteResponse> getTodayRoute(@AuthenticationPrincipal Jwt jwt) {
    UUID farmerId = userService.getAuthenticatedUser(jwt).getId();
    return ResponseEntity.ok(routeService.calculateTodayRoute(farmerId));
  }

  @PostMapping("/admin/geocode-batch")
  @Operation(
      summary = "Batch geocode addresses with missing coordinates",
      description = "Geocodes all addresses that have null latitude/longitude. Admin only.")
  public ResponseEntity<BatchGeocodeResponse> batchGeocode() {
    List<Address> pending = addressRepository.findAll()
        .stream()
        .filter(a -> a.getLatitude() == null)
        .toList();

    int processed = 0;
    int failed = 0;

    for (Address address : pending) {
      try {
        var coords = geocodingService.geocode(address);
        if (coords.isPresent()) {
          address.setLatitude(BigDecimal.valueOf(coords.get().latitude()));
          address.setLongitude(BigDecimal.valueOf(coords.get().longitude()));
          addressRepository.save(address);
          processed++;
        } else {
          failed++;
        }
        // Respect Nominatim's 1 request/second policy
        Thread.sleep(1100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.warn("Batch geocode failed for address {}: {}", address.getId(), e.getMessage());
        failed++;
      }
    }

    log.info("Batch geocode complete: processed={}, failed={}", processed, failed);
    return ResponseEntity.ok(new BatchGeocodeResponse(processed, failed));
  }
}
