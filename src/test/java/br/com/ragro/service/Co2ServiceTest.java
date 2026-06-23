package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.Co2CalculationRequest;
import br.com.ragro.controller.request.RecordCo2SavingRequest;
import br.com.ragro.controller.response.Co2CalculationResponse;
import br.com.ragro.controller.response.Co2EmissionResponse;
import br.com.ragro.controller.response.VehiclePreferenceResponse;
import br.com.ragro.domain.Co2Emission;
import br.com.ragro.domain.Co2Saving;
import br.com.ragro.domain.User;
import br.com.ragro.domain.VehiclePreference;
import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.repository.Co2EmissionRepository;
import br.com.ragro.repository.Co2SavingRepository;
import br.com.ragro.repository.VehiclePreferenceRepository;

import java.util.List;
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

  private User buildUser(UUID id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  private Jwt jwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("sub", "sub-123")
        .build();
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

   @Test
  void shouldThrowWhenConsumptionIsExactlyZero() {
    Co2CalculationRequest request = new Co2CalculationRequest();
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.GASOLINE);
    request.setDistanceKm(10.0);
    request.setAverageConsumption(0.0);
 
    assertThatThrownBy(() -> co2Service.calculate(request, null))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Consumo médio é obrigatório para este veículo e combustível.");
  }
 
  @Test
  void shouldNotThrowWhenConsumptionIsJustAboveZero() {
    Co2CalculationRequest request = new Co2CalculationRequest();
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.GASOLINE);
    request.setDistanceKm(10.0);
    request.setAverageConsumption(0.01);
 
    Co2CalculationResponse response = co2Service.calculate(request, null);
 
    assertThat(response.getCo2Emission()).isGreaterThan(0.0);
  }
 
  // ─── calculate: negated conditional on fuelType != ELECTRIC at line 53 ───
 
  @Test
  void shouldLookUpSavedPreference_whenConsumptionNullAndUserAuthenticated() {
    UUID userId = UUID.randomUUID();
    User user = buildUser(userId);
    Jwt jwt = jwt();
 
    VehiclePreference pref = new VehiclePreference();
    pref.setVehicleType(VehicleType.CAR);
    pref.setFuelType(FuelType.GASOLINE);
    pref.setAverageConsumption(12.0);
 
    Co2CalculationRequest request = new Co2CalculationRequest();
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.GASOLINE);
    request.setDistanceKm(12.0);
    request.setAverageConsumption(null);
 
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(vehiclePreferenceRepository.findById(userId)).thenReturn(Optional.of(pref));
    when(vehiclePreferenceRepository.save(any(VehiclePreference.class)))
        .thenAnswer(inv -> inv.getArgument(0));
 
    Co2CalculationResponse response = co2Service.calculate(request, jwt);
 
    assertThat(response.getAverageConsumption()).isEqualTo(12.0);
  }
 
  // ─── getUserPreference ─────────────────────────────────────────────────
 
  @Test
  void getUserPreference_shouldReturnMappedPreference_whenPresent() {
    UUID userId = UUID.randomUUID();
    User user = buildUser(userId);
    Jwt jwt = jwt();
 
    VehiclePreference pref = new VehiclePreference();
    pref.setVehicleType(VehicleType.CAR);
    pref.setFuelType(FuelType.GASOLINE);
    pref.setAverageConsumption(9.5);
 
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(vehiclePreferenceRepository.findById(userId)).thenReturn(Optional.of(pref));
 
    Optional<VehiclePreferenceResponse> result = co2Service.getUserPreference(jwt);
 
    assertThat(result).isPresent();
    assertThat(result.get().getVehicleType()).isEqualTo(VehicleType.CAR);
    assertThat(result.get().getFuelType()).isEqualTo(FuelType.GASOLINE);
    assertThat(result.get().getAverageConsumption()).isEqualTo(9.5);
  }
 
  @Test
  void getUserPreference_shouldReturnEmpty_whenNoPreferenceSaved() {
    UUID userId = UUID.randomUUID();
    User user = buildUser(userId);
    Jwt jwt = jwt();
 
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(vehiclePreferenceRepository.findById(userId)).thenReturn(Optional.empty());
 
    Optional<VehiclePreferenceResponse> result = co2Service.getUserPreference(jwt);
 
    assertThat(result).isEmpty();
  }
 
  // ─── getEmissionsHistory ────────────────────────────────────────────────
 
  @Test
  void getEmissionsHistory_shouldMapAllFields() {
    UUID userId = UUID.randomUUID();
    User user = buildUser(userId);
    Jwt jwt = jwt();
 
    Co2Emission emission = new Co2Emission();
    emission.setId(UUID.randomUUID());
    emission.setRouteDistanceKm(15.5);
    emission.setCo2Emission(3.21);
    emission.setVehicleType(VehicleType.MOTORCYCLE);
    emission.setFuelType(FuelType.ETHANOL);
    emission.setAverageConsumption(20.0);
 
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(co2EmissionRepository.findByVehiclePreferenceUserIdOrderByCreatedAtDesc(userId))
        .thenReturn(List.of(emission));
 
    List<Co2EmissionResponse> result = co2Service.getEmissionsHistory(jwt);
 
    assertThat(result).hasSize(1);
    Co2EmissionResponse resp = result.get(0);
    assertThat(resp.getId()).isEqualTo(emission.getId());
    assertThat(resp.getRouteDistanceKm()).isEqualTo(15.5);
    assertThat(resp.getCo2Emission()).isEqualTo(3.21);
    assertThat(resp.getVehicleType()).isEqualTo(VehicleType.MOTORCYCLE);
    assertThat(resp.getFuelType()).isEqualTo(FuelType.ETHANOL);
    assertThat(resp.getAverageConsumption()).isEqualTo(20.0);
  }
 
  @Test
  void getEmissionsHistory_shouldReturnEmptyList_whenNoEmissions() {
    UUID userId = UUID.randomUUID();
    User user = buildUser(userId);
    Jwt jwt = jwt();
 
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(co2EmissionRepository.findByVehiclePreferenceUserIdOrderByCreatedAtDesc(userId))
        .thenReturn(List.of());
 
    List<Co2EmissionResponse> result = co2Service.getEmissionsHistory(jwt);
 
    assertThat(result).isEmpty();
  }
 
  // ─── getTotalCo2Saved ───────────────────────────────────────────────────
 
  @Test
  void getTotalCo2Saved_shouldReturnRepositorySum() {
    when(co2SavingRepository.sumAllCo2Saved()).thenReturn(123.45);
 
    double result = co2Service.getTotalCo2Saved();
 
    assertThat(result).isEqualTo(123.45);
  }
 
  // ─── recordSaving ──────────────────────────────
 
  @Test
  void recordSaving_shouldUseProvidedConsumption_andSeparateDeliveryDistances() {
    UUID userId = UUID.randomUUID();
    User user = buildUser(userId);
    Jwt jwt = jwt();
 
    RecordCo2SavingRequest request = new RecordCo2SavingRequest();
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.GASOLINE);
    request.setAverageConsumption(10.0);
    request.setDistanceOptimized(20.0);
    request.setSeparateDeliveryDistances(List.of(5.0, 5.0)); // sum*2 = 20.0 non-optimized
 
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(vehiclePreferenceRepository.findById(userId)).thenReturn(Optional.empty());
    when(vehiclePreferenceRepository.save(any(VehiclePreference.class)))
        .thenAnswer(inv -> inv.getArgument(0));
 
    co2Service.recordSaving(request, jwt);
 
    ArgumentCaptor<Co2Saving> captor = ArgumentCaptor.forClass(Co2Saving.class);
    verify(co2SavingRepository).save(captor.capture());
    Co2Saving saved = captor.getValue();
 
    assertThat(saved.getUser()).isEqualTo(user);
    assertThat(saved.getDistanceOptimized()).isEqualTo(20.0);
    assertThat(saved.getDistanceNonOptimized()).isEqualTo(20.0);
    // non-optimized (20/10*2.31=4.62) - optimized (20/10*2.31=4.62) = 0.0
    assertThat(saved.getCo2Saved()).isEqualTo(0.0);
    assertThat(saved.getVehicleType()).isEqualTo(VehicleType.CAR);
    assertThat(saved.getFuelType()).isEqualTo(FuelType.GASOLINE);
    assertThat(saved.getAverageConsumption()).isEqualTo(10.0);
 
    verify(co2EmissionRepository).save(any(Co2Emission.class));
  }
 
  @Test
  void recordSaving_shouldUseDistanceNonOptimized_whenSeparateDistancesAbsent() {
    UUID userId = UUID.randomUUID();
    User user = buildUser(userId);
    Jwt jwt = jwt();
 
    RecordCo2SavingRequest request = new RecordCo2SavingRequest();
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.GASOLINE);
    request.setAverageConsumption(10.0);
    request.setDistanceOptimized(10.0);
    request.setDistanceNonOptimized(30.0);
 
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(vehiclePreferenceRepository.findById(userId)).thenReturn(Optional.empty());
    when(vehiclePreferenceRepository.save(any(VehiclePreference.class)))
        .thenAnswer(inv -> inv.getArgument(0));
 
    co2Service.recordSaving(request, jwt);
 
    ArgumentCaptor<Co2Saving> captor = ArgumentCaptor.forClass(Co2Saving.class);
    verify(co2SavingRepository).save(captor.capture());
    Co2Saving saved = captor.getValue();
 
    assertThat(saved.getDistanceNonOptimized()).isEqualTo(30.0);
    // nonOptimized: 30/10*2.31=6.93 ; optimized: 10/10*2.31=2.31 ; saved = 4.62
    assertThat(saved.getCo2Saved()).isEqualTo(4.62);
  }
 
  @Test
  void recordSaving_shouldThrow_whenNoDistanceInfoProvided() {
    UUID userId = UUID.randomUUID();
    User user = buildUser(userId);
    Jwt jwt = jwt();
 
    RecordCo2SavingRequest request = new RecordCo2SavingRequest();
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.GASOLINE);
    request.setAverageConsumption(10.0);
    request.setDistanceOptimized(10.0);
    // no separateDeliveryDistances, no distanceNonOptimized
 
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
 
    assertThatThrownBy(() -> co2Service.recordSaving(request, jwt))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("distância não otimizada");
 
    verify(co2SavingRepository, never()).save(any());
  }
 
  @Test
  void recordSaving_shouldFallBackToSavedPreference_whenConsumptionNotProvided() {
    UUID userId = UUID.randomUUID();
    User user = buildUser(userId);
    Jwt jwt = jwt();
 
    VehiclePreference savedPref = new VehiclePreference();
    savedPref.setVehicleType(VehicleType.CAR);
    savedPref.setFuelType(FuelType.GASOLINE);
    savedPref.setAverageConsumption(8.0);
 
    RecordCo2SavingRequest request = new RecordCo2SavingRequest();
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.GASOLINE);
    request.setAverageConsumption(null);
    request.setDistanceOptimized(10.0);
    request.setDistanceNonOptimized(20.0);
 
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(vehiclePreferenceRepository.findById(userId)).thenReturn(Optional.of(savedPref));
    when(vehiclePreferenceRepository.save(any(VehiclePreference.class)))
        .thenAnswer(inv -> inv.getArgument(0));
 
    co2Service.recordSaving(request, jwt);
 
    ArgumentCaptor<Co2Saving> captor = ArgumentCaptor.forClass(Co2Saving.class);
    verify(co2SavingRepository).save(captor.capture());
    assertThat(captor.getValue().getAverageConsumption()).isEqualTo(8.0);
  }
 
  @Test
  void recordSaving_shouldThrow_whenConsumptionNotProvidedAndNoSavedPreference() {
    UUID userId = UUID.randomUUID();
    User user = buildUser(userId);
    Jwt jwt = jwt();
 
    RecordCo2SavingRequest request = new RecordCo2SavingRequest();
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.GASOLINE);
    request.setAverageConsumption(null);
    request.setDistanceOptimized(10.0);
    request.setDistanceNonOptimized(20.0);
 
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(vehiclePreferenceRepository.findById(userId)).thenReturn(Optional.empty());
 
    assertThatThrownBy(() -> co2Service.recordSaving(request, jwt))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Consumo médio não informado");
  }
 
  @Test
  void recordSaving_shouldZeroOutEmission_forElectricVehicle() {
    UUID userId = UUID.randomUUID();
    User user = buildUser(userId);
    Jwt jwt = jwt();
 
    RecordCo2SavingRequest request = new RecordCo2SavingRequest();
    request.setVehicleType(VehicleType.CAR);
    request.setFuelType(FuelType.ELECTRIC);
    request.setAverageConsumption(10.0);
    request.setDistanceOptimized(10.0);
    request.setDistanceNonOptimized(20.0);
 
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(vehiclePreferenceRepository.findById(userId)).thenReturn(Optional.empty());
    when(vehiclePreferenceRepository.save(any(VehiclePreference.class)))
        .thenAnswer(inv -> inv.getArgument(0));
 
    co2Service.recordSaving(request, jwt);
 
    ArgumentCaptor<Co2Saving> captor = ArgumentCaptor.forClass(Co2Saving.class);
    verify(co2SavingRepository).save(captor.capture());
    assertThat(captor.getValue().getCo2Saved()).isEqualTo(0.0);
  }
 
  // ─── getOptions: motorcycle and light_truck fuel descriptions ──────────
 
  @Test
  void getOptions_shouldIncludeMotorcycleWithThreeFuels() {
    var motorcycle =
        co2Service.getOptions().getVehicles().stream()
            .filter(v -> v.getType() == VehicleType.MOTORCYCLE)
            .findFirst()
            .orElseThrow();
 
    assertThat(motorcycle.getAllowedFuels()).hasSize(3);
    assertThat(motorcycle.getAllowedFuels())
        .extracting(f -> f.getType())
        .containsExactlyInAnyOrder(FuelType.GASOLINE, FuelType.ETHANOL, FuelType.ELECTRIC);
    assertThat(motorcycle.getDescription()).isNotNull();
    motorcycle.getAllowedFuels().forEach(f -> assertThat(f.getDescription()).isNotNull());
  }
 
  @Test
  void getOptions_shouldIncludeLightTruckWithDieselAndElectricOnly() {
    var lightTruck =
        co2Service.getOptions().getVehicles().stream()
            .filter(v -> v.getType() == VehicleType.LIGHT_TRUCK)
            .findFirst()
            .orElseThrow();
 
    assertThat(lightTruck.getAllowedFuels())
        .extracting(f -> f.getType())
        .containsExactlyInAnyOrder(FuelType.DIESEL, FuelType.ELECTRIC);
  }
}
