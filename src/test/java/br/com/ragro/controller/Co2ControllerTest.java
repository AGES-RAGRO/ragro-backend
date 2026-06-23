package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.ActiveUserFilter;
import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.Co2CalculationResponse;
import br.com.ragro.controller.response.Co2EmissionResponse;
import br.com.ragro.controller.response.Co2OptionsResponse;
import br.com.ragro.controller.response.VehiclePreferenceResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.domain.enums.VehicleType;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.Co2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(Co2Controller.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class, ActiveUserFilter.class})
class Co2ControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private Co2Service co2Service;
  @MockBean private UserRepository userRepository;

  // ─── POST /co2/calculate ────────────────────────────────────────────────

  @Test
  void calculate_shouldReturn200WithCalculation() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(co2Service.calculate(any(), any()))
        .thenReturn(
            Co2CalculationResponse.builder()
                .co2Emission(12.34)
                .distanceKm(100.0)
                .vehicleType(VehicleType.CAR)
                .fuelType(FuelType.GASOLINE)
                .averageConsumption(10.0)
                .build());

    mockMvc
        .perform(
            post("/co2/calculate")
                .with(customerJwt(sub))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(calculationRequestJson(100.0, "CAR", "GASOLINE", 10.0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.co2Emission").value(12.34))
        .andExpect(jsonPath("$.distanceKm").value(100.0))
        .andExpect(jsonPath("$.vehicleType").value("CAR"))
        .andExpect(jsonPath("$.fuelType").value("GASOLINE"));
  }

  @Test
  void calculate_shouldReturn400_whenDistanceIsMissing() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    String body =
        """
        {
          "vehicleType": "CAR",
          "fuelType": "GASOLINE",
          "averageConsumption": 10.0
        }
        """;

    mockMvc
        .perform(
            post("/co2/calculate")
                .with(customerJwt(sub))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void calculate_shouldReturn400_whenDistanceIsNegative() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            post("/co2/calculate")
                .with(customerJwt(sub))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(calculationRequestJson(-5.0, "CAR", "GASOLINE", 10.0)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void calculate_shouldReturn200_whenNoJwtProvided() throws Exception {
    when(co2Service.calculate(any(), eq(null)))
        .thenReturn(
            Co2CalculationResponse.builder()
                .co2Emission(12.34)
                .distanceKm(100.0)
                .vehicleType(VehicleType.CAR)
                .fuelType(FuelType.GASOLINE)
                .averageConsumption(10.0)
                .build());

    mockMvc
        .perform(
            post("/co2/calculate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(calculationRequestJson(100.0, "CAR", "GASOLINE", 10.0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.co2Emission").value(12.34));
  }

  // ─── GET /co2/preference ────────────────────────────────────────────────

  @Test
  void getPreference_shouldReturn200WithPreference_whenItExists() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(co2Service.getUserPreference(any()))
        .thenReturn(
            Optional.of(
                VehiclePreferenceResponse.builder()
                    .vehicleType(VehicleType.MOTORCYCLE)
                    .fuelType(FuelType.ETHANOL)
                    .averageConsumption(25.0)
                    .build()));

    mockMvc
        .perform(get("/co2/preference").with(customerJwt(sub)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vehicleType").value("MOTORCYCLE"))
        .andExpect(jsonPath("$.fuelType").value("ETHANOL"))
        .andExpect(jsonPath("$.averageConsumption").value(25.0));
  }

  @Test
  void getPreference_shouldReturn204_whenPreferenceDoesNotExist() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(co2Service.getUserPreference(any())).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/co2/preference").with(customerJwt(sub)))
        .andExpect(status().isNoContent());
  }

  // ─── GET /co2/emissions ─────────────────────────────────────────────────

  @Test
  void getEmissionsHistory_shouldReturn200WithList() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    UUID emissionId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(co2Service.getEmissionsHistory(any()))
        .thenReturn(
            List.of(
                Co2EmissionResponse.builder()
                    .id(emissionId)
                    .routeDistanceKm(50.0)
                    .co2Emission(5.0)
                    .vehicleType(VehicleType.CAR)
                    .fuelType(FuelType.GASOLINE)
                    .averageConsumption(10.0)
                    .createdAt(OffsetDateTime.now())
                    .build()));

    mockMvc
        .perform(get("/co2/emissions").with(customerJwt(sub)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(emissionId.toString()))
        .andExpect(jsonPath("$[0].co2Emission").value(5.0));
  }

  @Test
  void getEmissionsHistory_shouldReturn200WithEmptyList_whenNoHistory() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(co2Service.getEmissionsHistory(any())).thenReturn(List.of());

    mockMvc
        .perform(get("/co2/emissions").with(customerJwt(sub)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // ─── POST /co2/record-savings ───────────────────────────────────────────

  @Test
  void recordSavings_shouldReturn200_whenRequestIsValid() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    String body =
        """
        {
          "distanceOptimized": 40.0,
          "distanceNonOptimized": 60.0,
          "vehicleType": "CAR",
          "fuelType": "GASOLINE",
          "averageConsumption": 10.0
        }
        """;

    mockMvc
        .perform(
            post("/co2/record-savings")
                .with(customerJwt(sub))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void recordSavings_shouldReturn400_whenOptimizedDistanceIsMissing() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    String body =
        """
        {
          "distanceNonOptimized": 60.0,
          "vehicleType": "CAR",
          "fuelType": "GASOLINE",
          "averageConsumption": 10.0
        }
        """;

    mockMvc
        .perform(
            post("/co2/record-savings")
                .with(customerJwt(sub))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void recordSavings_shouldReturn401_whenNoJwtProvided() throws Exception {
    String body =
        """
        {
          "distanceOptimized": 40.0,
          "distanceNonOptimized": 60.0,
          "vehicleType": "CAR",
          "fuelType": "GASOLINE",
          "averageConsumption": 10.0
        }
        """;

    mockMvc
        .perform(
            post("/co2/record-savings")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
  }

  // ─── GET /co2/options ───────────────────────────────────────────────────

  @Test
  void getOptions_shouldReturn200WithVehicleOptions() throws Exception {
    when(co2Service.getOptions())
        .thenReturn(
            Co2OptionsResponse.builder()
                .vehicles(
                    List.of(
                        Co2OptionsResponse.VehicleOption.builder()
                            .type(VehicleType.CAR)
                            .description("Carro")
                            .allowedFuels(
                                List.of(
                                    Co2OptionsResponse.FuelOption.builder()
                                        .type(FuelType.GASOLINE)
                                        .description("Gasolina")
                                        .build()))
                            .build()))
                .build());

    mockMvc
        .perform(get("/co2/options"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vehicles.length()").value(1))
        .andExpect(jsonPath("$.vehicles[0].type").value("CAR"))
        .andExpect(jsonPath("$.vehicles[0].allowedFuels[0].type").value("GASOLINE"));
  }

  @Test
  void getOptions_shouldReturn200_withoutAuthentication() throws Exception {
    when(co2Service.getOptions()).thenReturn(Co2OptionsResponse.builder().vehicles(List.of()).build());

    mockMvc.perform(get("/co2/options")).andExpect(status().isOk());
  }

  // ─── GET /co2/total-saved ───────────────────────────────────────────────

  @Test
  void getTotalCo2Saved_shouldReturn200WithTotal() throws Exception {
    when(co2Service.getTotalCo2Saved()).thenReturn(123.45);

    mockMvc
        .perform(get("/co2/total-saved"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCo2Saved").value(123.45));
  }

  @Test
  void getTotalCo2Saved_shouldReturn200WithZero_whenNoSavingsYet() throws Exception {
    when(co2Service.getTotalCo2Saved()).thenReturn(0.0);

    mockMvc
        .perform(get("/co2/total-saved"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCo2Saved").value(0.0));
  }

  // ─── helpers ─────────────────────────────────────────────────────────

  private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customerJwt(String sub) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
  }

  private String calculationRequestJson(
      double distanceKm, String vehicleType, String fuelType, double averageConsumption) {
    return """
        {
          "distanceKm": %s,
          "vehicleType": "%s",
          "fuelType": "%s",
          "averageConsumption": %s
        }
        """
        .formatted(distanceKm, vehicleType, fuelType, averageConsumption);
  }

  private User buildUser(String authSub) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test User");
    user.setEmail("user@test.com");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(true);
    user.setAuthSub(authSub);
    return user;
  }
}