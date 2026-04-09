package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.ProducerService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProducerController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class ProducerControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private ProducerService producerService;

  @MockBean private UserRepository userRepository;

  @MockBean private ProducerRepository producerRepository;

  @Test
  void shouldReturn200_whenProducerExists() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-active";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    ProducerGetResponse response = ProducerGetResponse.builder()
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
            get("/farmer/" + producerId)
                .with(SecurityMockMvcRequestPostProcessors.jwt()
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
            get("/farmer/" + producerId)
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                    .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn403_whenUserIsInactive() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-inactive";
    User inactiveUser = buildUser(sub, false);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(inactiveUser));

    mockMvc
        .perform(
            get("/farmer/" + producerId)
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                    .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Conta desativada"));
  }

  @Test
  void shouldReturn403_whenUserNotFoundInDatabase() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-unknown";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/farmer/" + producerId)
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                    .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Conta desativada"));
  }

  @Test
  void shouldReturn401_whenNoTokenProvided() throws Exception {
    UUID producerId = UUID.randomUUID();
    mockMvc
        .perform(get("/farmer/" + producerId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturn403_whenTokenHasWrongRole() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "some-sub";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    mockMvc
        .perform(
            get("/farmer/" + producerId)
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                    .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
  }

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
