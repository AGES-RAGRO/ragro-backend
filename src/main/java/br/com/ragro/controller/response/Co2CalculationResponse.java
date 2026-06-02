package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Co2CalculationResponse {
  private Double co2Emission;
  private Double distanceKm;
  private VehicleType vehicleType;
  private FuelType fuelType;
  private Double averageConsumption;
}
