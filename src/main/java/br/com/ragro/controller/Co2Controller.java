package br.com.ragro.controller;

import br.com.ragro.controller.request.Co2CalculationRequest;
import br.com.ragro.controller.response.Co2CalculationResponse;
import br.com.ragro.controller.response.Co2OptionsResponse;
import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import br.com.ragro.service.Co2Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/co2")
@RequiredArgsConstructor
@Tag(name = "CO2", description = "CO2 emission calculation operations")
public class Co2Controller {

  private final Co2Service co2Service;

  @PostMapping("/calculate")
  @Operation(summary = "Calculate CO2 emission", description = "Calculates CO2 emission based on distance, vehicle type and fuel type.")
  public ResponseEntity<Co2CalculationResponse> calculate(
      @Valid @RequestBody Co2CalculationRequest request) {
    return ResponseEntity.ok(co2Service.calculate(request));
  }

  @GetMapping("/default-consumption")
  @Operation(
      summary = "Get default consumption",
      description = "Returns the default average consumption for a given vehicle and fuel type.")
  public ResponseEntity<Double> getDefaultConsumption(
      @RequestParam VehicleType vehicleType, @RequestParam FuelType fuelType) {
    return ResponseEntity.ok(co2Service.getDefaultConsumption(vehicleType, fuelType));
  }

  @GetMapping("/options")
  @Operation(
      summary = "Get CO2 calculation options",
      description = "Returns all available vehicle types, their allowed fuels and default consumptions.")
  public ResponseEntity<Co2OptionsResponse> getOptions() {
    return ResponseEntity.ok(co2Service.getOptions());
  }
}
