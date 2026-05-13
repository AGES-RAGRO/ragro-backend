package br.com.ragro.controller;

import br.com.ragro.controller.request.Co2CalculationRequest;
import br.com.ragro.controller.request.RecordCo2SavingRequest;
import br.com.ragro.controller.response.Co2CalculationResponse;
import br.com.ragro.controller.response.Co2EmissionResponse;
import br.com.ragro.controller.response.Co2OptionsResponse;
import br.com.ragro.controller.response.VehiclePreferenceResponse;
import br.com.ragro.service.Co2Service;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/co2")
@RequiredArgsConstructor
@Tag(name = "CO2", description = "CO2 emission calculation and savings operations")
public class Co2Controller {

  private final Co2Service co2Service;

  @PostMapping("/calculate")
  @Operation(summary = "Calculate CO2 emission", description = "Calculates CO2 emission based on distance, vehicle type and fuel type. Automatically saves preferences for authenticated users.")
  public ResponseEntity<Co2CalculationResponse> calculate(
      @Valid @RequestBody Co2CalculationRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(co2Service.calculate(request, jwt));
  }

  @GetMapping("/preference")
  @Operation(summary = "Get saved vehicle preference", description = "Returns the saved vehicle and fuel preference for the authenticated user.")
  public ResponseEntity<VehiclePreferenceResponse> getPreference(
      @AuthenticationPrincipal Jwt jwt) {
    return co2Service.getUserPreference(jwt)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.noContent().build());
  }

  @GetMapping("/emissions")
  @Operation(summary = "Get emissions history", description = "Returns the CO2 emissions history for the authenticated user.")
  public ResponseEntity<List<Co2EmissionResponse>> getEmissionsHistory(
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(co2Service.getEmissionsHistory(jwt));
  }

  @PostMapping("/record-savings")
  @Operation(summary = "Record CO2 savings", description = "Calculates and logs CO2 savings between an optimized and non-optimized route.")
  public ResponseEntity<Void> recordSavings(
      @Valid @RequestBody RecordCo2SavingRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    co2Service.recordSaving(request, jwt);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/options")
  @Operation(summary = "Get CO2 calculation options", description = "Returns all available vehicle types and their allowed fuels.")
  public ResponseEntity<Co2OptionsResponse> getOptions() {
    return ResponseEntity.ok(co2Service.getOptions());
  }
}
