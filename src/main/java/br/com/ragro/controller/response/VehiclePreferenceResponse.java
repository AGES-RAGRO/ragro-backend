package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VehiclePreferenceResponse {
  private VehicleType vehicleType;
  private FuelType fuelType;
  private Double averageConsumption;
}
