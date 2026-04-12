package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.ProducerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

@WebMvcTest(ProducerController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class ProducerControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private ProducerService producerService;
  @MockBean private UserRepository userRepository;
  @MockBean private ProducerRepository producerRepository;

  // ─── GET /{id} ──────────────────────────────────────────────────────────────

  @Test
  void shouldReturn200_whenProducerExists() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-active";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    ProducerGetResponse response =
        ProducerGetResponse.builder()
            .id(producerId)
            .name("Test Farmer")
            .email("farmer@test.com")
            .phone("51999999999")
            .fiscalNumber("12345678901234")
            .fiscalNumberType("CNPJ")
            .farmName("Fazenda Teste")
            .totalReviews(0)
            .averageRating(BigDecimal.ZERO)
            .totalOrders(0)
            .totalSalesAmount(BigDecimal.ZERO)
            .build();

    when(producerService.getProducerProfileById(producerId)).thenReturn(response);

    mockMvc
        .perform(
            get("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Farmer"))
        .andExpect(jsonPath("$.farmName").value("Fazenda Teste"));
  }

  @Test
  void shouldReturn404_whenProducerNotFound() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-active";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    when(producerService.getProducerProfileById(producerId))
        .thenThrow(new NotFoundException("Produtor não encontrado"));

    mockMvc
        .perform(
            get("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isNotFound());
  }

    @Test
    void shouldReturn401_whenUserIsInactive() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-inactive";
    User inactiveUser = buildUser(sub, false);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(inactiveUser));

    mockMvc
        .perform(
            get("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Conta desativada ou usuário não encontrado"));
  }

    @Test
    void shouldReturn401_whenUserNotFoundInDatabase() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-unknown";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Conta desativada ou usuário não encontrado"));
  }

  @Test
  void shouldReturn401_whenNoTokenProvided() throws Exception {
    UUID producerId = UUID.randomUUID();
    mockMvc.perform(get("/producers/" + producerId)).andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturn403_whenTokenHasWrongRole() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "some-sub";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    mockMvc
        .perform(
            get("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
  }

  // ─── PUT /{id} ──────────────────────────────────────────────────────────────

  @Test
  void putProducer_shouldReturn200_whenFarmerUpdatesOwnProfile() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-farmer";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("João Atualizado");
    request.setFarmName("Fazenda Nova");

    ProducerGetResponse response =
        ProducerGetResponse.builder()
            .id(producerId)
            .name("João Atualizado")
            .email("farmer@test.com")
            .phone("51999999999")
            .farmName("Fazenda Nova")
            .fiscalNumber("12345678901")
            .fiscalNumberType("CPF")
            .totalReviews(0)
            .averageRating(BigDecimal.ZERO)
            .totalOrders(0)
            .totalSalesAmount(BigDecimal.ZERO)
            .build();

    when(producerService.updateProducerProfile(eq(producerId), any(), any())).thenReturn(response);

    mockMvc
        .perform(
            put("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("João Atualizado"))
        .andExpect(jsonPath("$.farmName").value("Fazenda Nova"));
  }

    @Test
    void putProducer_shouldReturn403_whenFarmerTriesToUpdateAnotherProfile() throws Exception {
    UUID otherProducerId = UUID.randomUUID();
    String sub = "keycloak-sub-farmer";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Hacker");

    when(producerService.updateProducerProfile(eq(otherProducerId), any(), any()))
        .thenThrow(new ForbiddenException("Você não tem permissão para alterar este perfil"));

    mockMvc
        .perform(
            put("/producers/" + otherProducerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void putProducer_shouldReturn401_whenNoTokenProvided() throws Exception {
    UUID producerId = UUID.randomUUID();
    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Test");

    mockMvc
        .perform(
            put("/producers/" + producerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void putProducer_shouldReturn403_whenRoleIsNotFarmer() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "some-customer-sub";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Test");

    mockMvc
        .perform(
            put("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "cust@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

    @Test
    void putProducer_shouldReturn401_whenUserIsInactive() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-inactive";
    User inactiveUser = buildUser(sub, false);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(inactiveUser));

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Test");

    mockMvc
        .perform(
            put("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Conta desativada ou usuário não encontrado"));
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  private User buildUser(String authSub, boolean active) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test Farmer");
    user.setEmail("farmer@test.com");
    user.setPhone("51999999999");
    user.setType(TypeUser.FARMER);
    user.setActive(active);
    user.setAuthSub(authSub);
    user.setCreatedAt(OffsetDateTime.now().minusDays(1));
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }
}
