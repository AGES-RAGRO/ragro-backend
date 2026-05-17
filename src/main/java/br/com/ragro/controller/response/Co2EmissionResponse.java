package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Co2EmissionResponse {
  private UUID id;
  private Double routeDistanceKm;
  private Double co2Emission;
  private VehicleType vehicleType;
  private FuelType fuelType;
  private Double averageConsumption;
  private OffsetDateTime createdAt;
}
