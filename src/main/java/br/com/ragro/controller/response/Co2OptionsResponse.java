package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Co2OptionsResponse {

  private final List<VehicleOption> vehicles;

  @Getter
  @Builder
  public static class VehicleOption {
    private final VehicleType type;
    private final String description;
    private final List<FuelOption> allowedFuels;
  }

  @Getter
  @Builder
  public static class FuelOption {
    private final FuelType type;
    private final String description;
    private final Double defaultAverageConsumption;
  }
}
