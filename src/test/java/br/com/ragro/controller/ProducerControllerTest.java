package br.com.ragro.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProducerController.class)
class ProducerControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private UserRepository userRepository;

  @Test
  void shouldReturn200_whenProducerIsActive() throws Exception {
    String sub = "keycloak-sub-active";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    mockMvc
        .perform(
            get("/farmer/dashboard")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isOk());
  }

  @Test
  void shouldReturn403_whenProducerIsInactive() throws Exception {
    String sub = "keycloak-sub-inactive";
    User inactiveUser = buildUser(sub, false);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(inactiveUser));

    mockMvc
        .perform(
            get("/farmer/dashboard")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Conta desativada"));
  }

  @Test
  void shouldReturn403_whenProducerNotFoundInDatabase() throws Exception {
    String sub = "keycloak-sub-unknown";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/farmer/dashboard")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Conta desativada"));
  }

  @Test
  void shouldReturn401_whenNoTokenProvided() throws Exception {
    mockMvc
        .perform(get("/farmer/dashboard"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturn403_whenTokenHasWrongRole() throws Exception {
    mockMvc
        .perform(
            get("/farmer/dashboard")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.claim("sub", "some-sub").claim("email", "customer@test.com"))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))))
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
