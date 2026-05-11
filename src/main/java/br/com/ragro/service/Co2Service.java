package br.com.ragro.service;

import br.com.ragro.controller.request.Co2CalculationRequest;
import br.com.ragro.controller.request.RecordCo2SavingRequest;
import br.com.ragro.controller.response.Co2CalculationResponse;
import br.com.ragro.controller.response.Co2OptionsResponse;
import br.com.ragro.controller.response.VehiclePreferenceResponse;
import br.com.ragro.domain.Co2Saving;
import br.com.ragro.domain.User;
import br.com.ragro.domain.VehiclePreference;
import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.repository.Co2SavingRepository;
import br.com.ragro.repository.VehiclePreferenceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class Co2Service {

  private final VehiclePreferenceRepository vehiclePreferenceRepository;
  private final Co2SavingRepository co2SavingRepository;
  private final UserService userService;

  private static final Map<VehicleType, List<FuelType>> ALLOWED_FUELS_BY_VEHICLE = Map.of(
      VehicleType.MOTORCYCLE, List.of(FuelType.GASOLINE, FuelType.ETHANOL, FuelType.ELECTRIC),
      VehicleType.CAR, List.of(FuelType.GASOLINE, FuelType.ETHANOL, FuelType.DIESEL, FuelType.ELECTRIC),
      VehicleType.VAN, List.of(FuelType.GASOLINE, FuelType.DIESEL, FuelType.ELECTRIC),
      VehicleType.LIGHT_TRUCK, List.of(FuelType.DIESEL, FuelType.ELECTRIC)
  );

  @Transactional
  public Co2CalculationResponse calculate(Co2CalculationRequest request, Jwt jwt) {
    validate(request);

    User user = (jwt != null) ? userService.getAuthenticatedUser(jwt) : null;
    Double consumption = request.getAverageConsumption();

    if (consumption == null && request.getFuelType() != FuelType.ELECTRIC && user != null) {
      consumption = vehiclePreferenceRepository.findById(user.getId())
          .filter(pref -> pref.getVehicleType() == request.getVehicleType() && pref.getFuelType() == request.getFuelType())
          .map(VehiclePreference::getAverageConsumption)
          .orElse(null);
    }

    if (request.getFuelType() != FuelType.ELECTRIC && (consumption == null || consumption <= 0)) {
      throw new BusinessException("Consumo médio é obrigatório para este veículo e combustível.");
    }

    double co2Emission = 0.0;
    if (request.getFuelType() != FuelType.ELECTRIC) {
      co2Emission = (request.getDistanceKm() / consumption) * request.getFuelType().getEmissionFactor();
    }

    if (user != null && consumption != null) {
      saveOrUpdatePreference(user, request.getVehicleType(), request.getFuelType(), consumption);
    }

    return Co2CalculationResponse.builder()
        .co2Emission(round(co2Emission))
        .distanceKm(request.getDistanceKm())
        .vehicleType(request.getVehicleType())
        .fuelType(request.getFuelType())
        .averageConsumption(consumption)
        .build();
  }

  @Transactional(readOnly = true)
  public Optional<VehiclePreferenceResponse> getUserPreference(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    return vehiclePreferenceRepository.findById(user.getId())
        .map(pref -> VehiclePreferenceResponse.builder()
            .vehicleType(pref.getVehicleType())
            .fuelType(pref.getFuelType())
            .averageConsumption(pref.getAverageConsumption())
            .build());
  }

  @Transactional
  public void recordSaving(RecordCo2SavingRequest request, Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    
    Double consumption = request.getAverageConsumption();
    if (consumption == null) {
      consumption = vehiclePreferenceRepository.findById(user.getId())
          .filter(pref -> pref.getVehicleType() == request.getVehicleType() && pref.getFuelType() == request.getFuelType())
          .map(VehiclePreference::getAverageConsumption)
          .orElseThrow(() -> new BusinessException("Consumo médio não informado e sem preferência salva."));
    }

    double co2NonOptimized = 0.0;
    double co2Optimized = 0.0;
    
    if (request.getFuelType() != FuelType.ELECTRIC) {
      co2NonOptimized = (request.getDistanceNonOptimized() / consumption) * request.getFuelType().getEmissionFactor();
      co2Optimized = (request.getDistanceOptimized() / consumption) * request.getFuelType().getEmissionFactor();
    }

    double co2Saved = co2NonOptimized - co2Optimized;

    Co2Saving saving = new Co2Saving();
    saving.setUser(user);
    saving.setDistanceOptimized(request.getDistanceOptimized());
    saving.setDistanceNonOptimized(request.getDistanceNonOptimized());
    saving.setCo2Saved(round(co2Saved));
    saving.setVehicleType(request.getVehicleType());
    saving.setFuelType(request.getFuelType());
    saving.setAverageConsumption(consumption);
    co2SavingRepository.save(saving);

    saveOrUpdatePreference(user, request.getVehicleType(), request.getFuelType(), consumption);
  }

  private void saveOrUpdatePreference(User user, VehicleType vehicleType, FuelType fuelType, Double consumption) {
    VehiclePreference preference = vehiclePreferenceRepository.findById(user.getId())
        .orElse(new VehiclePreference());
    
    preference.setUser(user);
    preference.setUserId(user.getId());
    preference.setVehicleType(vehicleType);
    preference.setFuelType(fuelType);
    preference.setAverageConsumption(consumption);
    vehiclePreferenceRepository.save(preference);
  }

  private void validate(Co2CalculationRequest request) {
    List<FuelType> allowedFuels = ALLOWED_FUELS_BY_VEHICLE.get(request.getVehicleType());
    if (allowedFuels == null || !allowedFuels.contains(request.getFuelType())) {
      throw new BusinessException("Tipo de combustível não permitido para este veículo.");
    }
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
