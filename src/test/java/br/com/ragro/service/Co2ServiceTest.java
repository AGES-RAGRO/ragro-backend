package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.Co2CalculationRequest;
import br.com.ragro.controller.response.Co2CalculationResponse;
import br.com.ragro.domain.Co2Emission;
import br.com.ragro.domain.User;
import br.com.ragro.domain.VehiclePreference;
import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.repository.Co2EmissionRepository;
import br.com.ragro.repository.Co2SavingRepository;
import br.com.ragro.repository.VehiclePreferenceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class Co2ServiceTest {

  @Mock private VehiclePreferenceRepository vehiclePreferenceRepository;
  @Mock private Co2SavingRepository co2SavingRepository;
  @Mock private Co2EmissionRepository co2EmissionRepository;
  @Mock private UserService userService;

  @InjectMocks private Co2Service co2Service;

  private static Co2CalculationRequest request(
      VehicleType vehicle, FuelType fuel, Double distance, Double consumption) {
    Co2CalculationRequest request = new Co2CalculationRequest();
    request.setDistanceKm(distance);
    request.setVehicleType(vehicle);
    request.setFuelType(fuel);
    request.setAverageConsumption(consumption);
    return request;
  }

  @Test
  void shouldCalculateCo2WithProvidedConsumption() {
    Co2CalculationResponse response =
        co2Service.calculate(request(VehicleType.CAR, FuelType.GASOLINE, 10.2, 10.0), null);

    // 10.2 / 10.0 * 2.31 = 2.3562 -> 2.36
    assertThat(response.getCo2Emission()).isEqualTo(2.36);
    assertThat(response.getAverageConsumption()).isEqualTo(10.0);
    verify(co2EmissionRepository, never()).save(any());
  }

  @Test
  void shouldReturnZeroEmissionForElectricVehicle() {
    Co2CalculationResponse response =
        co2Service.calculate(request(VehicleType.CAR, FuelType.ELECTRIC, 10.2, null), null);

    assertThat(response.getCo2Emission()).isEqualTo(0.0);
  }

  @Test
  void shouldThrowWhenFuelNotAllowedForVehicle() {
    Co2CalculationRequest request = request(VehicleType.LIGHT_TRUCK, FuelType.GASOLINE, 10.2, 8.0);

    assertThatThrownBy(() -> co2Service.calculate(request, null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void shouldThrowWhenConsumptionMissingForCombustionVehicle() {
    Co2CalculationRequest request = request(VehicleType.CAR, FuelType.GASOLINE, 10.2, null);

    assertThatThrownBy(() -> co2Service.calculate(request, null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void shouldPersistEmissionLinkedToVehiclePreferenceForAuthenticatedUser() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").claim("sub", "user-sub").build();

    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(vehiclePreferenceRepository.findById(userId)).thenReturn(Optional.empty());
    when(vehiclePreferenceRepository.save(any(VehiclePreference.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    co2Service.calculate(request(VehicleType.CAR, FuelType.GASOLINE, 10.2, 10.0), jwt);

    ArgumentCaptor<Co2Emission> captor = ArgumentCaptor.forClass(Co2Emission.class);
    verify(co2EmissionRepository).save(captor.capture());

    Co2Emission saved = captor.getValue();
    assertThat(saved.getVehiclePreference()).isNotNull();
    assertThat(saved.getVehiclePreference().getUser().getId()).isEqualTo(userId);
    assertThat(saved.getRouteDistanceKm()).isEqualTo(10.2);
    assertThat(saved.getCo2Emission()).isEqualTo(2.36);
    assertThat(saved.getVehicleType()).isEqualTo(VehicleType.CAR);
    assertThat(saved.getFuelType()).isEqualTo(FuelType.GASOLINE);
    assertThat(saved.getAverageConsumption()).isEqualTo(10.0);
  }

  @Test
  void shouldReturnAllVehicleOptions() {
    assertThat(co2Service.getOptions().getVehicles()).hasSize(4);
  }
}
