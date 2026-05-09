package br.com.ragro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.ragro.controller.request.Co2CalculationRequest;
import br.com.ragro.controller.response.Co2CalculationResponse;
import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import br.com.ragro.exception.BusinessException;
import org.junit.jupiter.api.Test;

class Co2ServiceTest {

  private final Co2Service co2Service = new Co2Service();

  @Test
  void shouldCalculateCo2ForMotorcycleWithGasoline() {
    Co2CalculationRequest request = new Co2CalculationRequest();
    request.setDistanceKm(10.2);
    request.setVehicleType(VehicleType.MOTORCYCLE);
    request.setFuelType(FuelType.GASOLINE);

    Co2CalculationResponse response = co2Service.calculate(request);

    assertEquals(0.79, response.getCo2Emission());
    assertEquals(30.0, response.getAverageConsumption());
  }

  @Test
  void shouldCalculateCo2ForCarWithGasoline() {
    Co2CalculationRequest request = new Co2CalculationRequest();
    request.setDistanceKm(10.2);
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.GASOLINE);

    Co2CalculationResponse response = co2Service.calculate(request);

    assertEquals(1.96, response.getCo2Emission());
    assertEquals(12.0, response.getAverageConsumption());
  }

  @Test
  void shouldCalculateCo2ForElectricVehicle() {
    Co2CalculationRequest request = new Co2CalculationRequest();
    request.setDistanceKm(10.2);
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.ELECTRIC);

    Co2CalculationResponse response = co2Service.calculate(request);

    assertEquals(0.0, response.getCo2Emission());
  }

  @Test
  void shouldThrowExceptionForInvalidFuelVehicleCombination() {
    Co2CalculationRequest request = new Co2CalculationRequest();
    request.setDistanceKm(10.2);
    request.setVehicleType(VehicleType.LIGHT_TRUCK);
    request.setFuelType(FuelType.GASOLINE); // Light truck only Diesel or Electric

    assertThrows(BusinessException.class, () -> co2Service.calculate(request));
  }

  @Test
  void shouldUseProvidedConsumptionIfPresent() {
    Co2CalculationRequest request = new Co2CalculationRequest();
    request.setDistanceKm(10.2);
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.GASOLINE);
    request.setAverageConsumption(10.0); // Override default 12.0

    Co2CalculationResponse response = co2Service.calculate(request);

    // 10.2 / 10.0 * 2.31 = 1.02 * 2.31 = 2.3562 -> 2.36
    assertEquals(2.36, response.getCo2Emission());
    assertEquals(10.0, response.getAverageConsumption());
  }

  @Test
  void shouldReturnOptionsWithMetadata() {
    var options = co2Service.getOptions();
    assertEquals(4, options.getVehicles().size());
  }
}
