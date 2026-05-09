package br.com.ragro.controller.request;

import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Co2CalculationRequest {

  @NotNull(message = "Distance is required")
  @Positive(message = "Distance must be positive")
  private Double distanceKm;

  @NotNull(message = "Vehicle type is required")
  private VehicleType vehicleType;

  @NotNull(message = "Fuel type is required")
  private FuelType fuelType;

  @Positive(message = "Average consumption must be positive")
  private Double averageConsumption;
}
