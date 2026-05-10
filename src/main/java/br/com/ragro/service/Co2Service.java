package br.com.ragro.service;

import br.com.ragro.controller.request.Co2CalculationRequest;
import br.com.ragro.controller.response.Co2CalculationResponse;
import br.com.ragro.controller.response.Co2OptionsResponse;
import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import br.com.ragro.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class Co2Service {

  private static final Map<VehicleType, List<FuelType>> ALLOWED_FUELS_BY_VEHICLE = Map.of(
      VehicleType.MOTORCYCLE, List.of(FuelType.GASOLINE, FuelType.ETHANOL, FuelType.ELECTRIC),
      VehicleType.CAR, List.of(FuelType.GASOLINE, FuelType.ETHANOL, FuelType.DIESEL, FuelType.ELECTRIC),
      VehicleType.VAN, List.of(FuelType.GASOLINE, FuelType.DIESEL, FuelType.ELECTRIC),
      VehicleType.LIGHT_TRUCK, List.of(FuelType.DIESEL, FuelType.ELECTRIC)
  );

  private static final Map<VehicleType, Map<FuelType, Double>> DEFAULT_CONSUMPTION = Map.of(
      VehicleType.MOTORCYCLE, Map.of(
          FuelType.GASOLINE, 30.0,
          FuelType.ETHANOL, 22.0
      ),
      VehicleType.CAR, Map.of(
          FuelType.GASOLINE, 12.0,
          FuelType.ETHANOL, 8.5,
          FuelType.DIESEL, 14.0
      ),
      VehicleType.VAN, Map.of(
          FuelType.GASOLINE, 8.0,
          FuelType.DIESEL, 8.0
      ),
      VehicleType.LIGHT_TRUCK, Map.of(
          FuelType.DIESEL, 6.0
      )
  );

  public Co2CalculationResponse calculate(Co2CalculationRequest request) {
    validate(request);

    Double consumption = request.getAverageConsumption();
    if (consumption == null && request.getFuelType() != FuelType.ELECTRIC) {
      consumption = getDefaultConsumption(request.getVehicleType(), request.getFuelType());
    }

    double co2Emission = 0.0;
    if (request.getFuelType() != FuelType.ELECTRIC) {
      if (consumption == null || consumption <= 0) {
        throw new BusinessException("Average consumption must be provided for this vehicle and fuel type");
      }
      co2Emission = (request.getDistanceKm() / consumption) * request.getFuelType().getEmissionFactor();
    }

    return Co2CalculationResponse.builder()
        .co2Emission(round(co2Emission))
        .distanceKm(request.getDistanceKm())
        .vehicleType(request.getVehicleType())
        .fuelType(request.getFuelType())
        .averageConsumption(consumption)
        .build();
  }

  private void validate(Co2CalculationRequest request) {
    List<FuelType> allowedFuels = ALLOWED_FUELS_BY_VEHICLE.get(request.getVehicleType());
    if (allowedFuels == null || !allowedFuels.contains(request.getFuelType())) {
      throw new BusinessException("Fuel type not allowed for this vehicle");
    }
  }

  public Double getDefaultConsumption(VehicleType vehicleType, FuelType fuelType) {
    Map<FuelType, Double> vehicleDefaults = DEFAULT_CONSUMPTION.get(vehicleType);
    return (vehicleDefaults != null) ? vehicleDefaults.get(fuelType) : null;
  }

  public Co2OptionsResponse getOptions() {
    List<Co2OptionsResponse.VehicleOption> vehicleOptions =
        ALLOWED_FUELS_BY_VEHICLE.entrySet().stream()
            .map(
                entry -> {
                  VehicleType vehicleType = entry.getKey();
                  List<FuelType> fuels = entry.getValue();

                  List<Co2OptionsResponse.FuelOption> fuelOptions =
                      fuels.stream()
                          .map(
                              fuelType ->
                                  Co2OptionsResponse.FuelOption.builder()
                                      .type(fuelType)
                                      .description(fuelType.getDescription())
                                      .defaultConsumption(
                                          getDefaultConsumption(vehicleType, fuelType))
                                      .build())
                          .toList();

                  return Co2OptionsResponse.VehicleOption.builder()
                      .type(vehicleType)
                      .description(vehicleType.getDescription())
                      .allowedFuels(fuelOptions)
                      .build();
                })
            .toList();

    return Co2OptionsResponse.builder().vehicles(vehicleOptions).build();
  }

  private double round(double value) {
    return BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.HALF_UP)
        .doubleValue();
  }
}
