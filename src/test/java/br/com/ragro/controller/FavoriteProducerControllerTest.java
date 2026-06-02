package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.FavoriteProducerResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.FavoriteProducerService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(FavoriteProducerController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class FavoriteProducerControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private FavoriteProducerService favoriteProducerService;
  @MockBean private JwtDecoder jwtDecoder;
  @MockBean private UserRepository userRepository;

  @BeforeEach
  void stubActiveUserFilter() {
    SecurityContextHolder.clearContext();
    User activeUser = new User();
    activeUser.setId(UUID.randomUUID());
    activeUser.setActive(true);
    activeUser.setType(TypeUser.CUSTOMER);
    when(userRepository.findByAuthSub(any())).thenReturn(Optional.of(activeUser));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void favoriteProducer_shouldReturn204_whenAuthenticatedAsCustomer() throws Exception {
    UUID producerId = UUID.randomUUID();

    mockMvc
        .perform(post("/customers/me/favorites/{producerId}", producerId).with(asCustomer()))
        .andExpect(status().isNoContent());

    verify(favoriteProducerService).favoriteProducer(any(UUID.class), any());
  }

  @Test
  void unfavoriteProducer_shouldReturn204_whenAuthenticatedAsCustomer() throws Exception {
    UUID producerId = UUID.randomUUID();

    mockMvc
        .perform(delete("/customers/me/favorites/{producerId}", producerId).with(asCustomer()))
        .andExpect(status().isNoContent());

    verify(favoriteProducerService).unfavoriteProducer(any(UUID.class), any());
  }

  @Test
  void getFavorites_shouldReturn200WithFavorites_whenAuthenticatedAsCustomer() throws Exception {
    UUID producerId = UUID.randomUUID();
    FavoriteProducerResponse response =
        FavoriteProducerResponse.builder()
            .producerId(producerId)
            .producerName("João Farmer")
            .farmName("Fazenda Boa Terra")
            .avatarUrl("avatars/farmer.jpg")
            .averageRating(new BigDecimal("4.70"))
            .build();

    when(favoriteProducerService.getFavorites(any())).thenReturn(List.of(response));

    mockMvc
        .perform(get("/customers/me/favorites").with(asCustomer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].producerId").value(producerId.toString()))
        .andExpect(jsonPath("$[0].producerName").value("João Farmer"))
        .andExpect(jsonPath("$[0].farmName").value("Fazenda Boa Terra"));
  }

  @Test
  void getFavorites_shouldReturn403_whenAuthenticatedWithoutCustomerRole() throws Exception {
    mockMvc
        .perform(get("/customers/me/favorites").with(asFarmer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getFavorites_shouldReturn401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/customers/me/favorites")).andExpect(status().isUnauthorized());
  }

  private static RequestPostProcessor asCustomer() {
    return Objects.requireNonNull(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
  }

  private static RequestPostProcessor asFarmer() {
    return Objects.requireNonNull(jwt().authorities(new SimpleGrantedAuthority("ROLE_FARMER")));
  }
}
