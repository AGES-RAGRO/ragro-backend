package br.com.ragro.controller.request;

import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecordCo2SavingRequest {

  @NotNull(message = "Optimized distance is required")
  @Positive(message = "Distance must be positive")
  private Double distanceOptimized;

  @Positive(message = "Distance must be positive")
  private Double distanceNonOptimized;

  private List<@Positive(message = "Each distance must be positive") Double>
      separateDeliveryDistances;

  @NotNull(message = "Vehicle type is required")
  private VehicleType vehicleType;

  @NotNull(message = "Fuel type is required")
  private FuelType fuelType;

  @Positive(message = "Average consumption must be positive")
  private Double averageConsumption;
}
