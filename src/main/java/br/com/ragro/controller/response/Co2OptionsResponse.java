package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Co2OptionsResponse {
  private List<VehicleOption> vehicles;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VehicleOption {
    private VehicleType type;
    private String description;
    private List<FuelOption> allowedFuels;
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FuelOption {
    private FuelType type;
    private String description;
  }
}
