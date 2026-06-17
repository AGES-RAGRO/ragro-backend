package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.NotificationService;
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

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class NotificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private NotificationService notificationService;
  @MockBean private UserRepository userRepository;

  @Test
  void registerToken_shouldReturn204AndCallService() throws Exception {
    String sub = "customer-sub";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildActiveUser(sub)));

    mockMvc
        .perform(
            post("/notifications/token")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"device-token-123\"}"))
        .andExpect(status().isNoContent());

    verify(notificationService).saveToken(any(), eq("device-token-123"));
  }

  @Test
  void registerToken_shouldReturn400WhenTokenBlank() throws Exception {
    String sub = "customer-sub";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildActiveUser(sub)));

    mockMvc
        .perform(
            post("/notifications/token")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"  \"}"))
        .andExpect(status().isBadRequest());

    verify(notificationService, never()).saveToken(any(), any());
  }

  @Test
  void registerToken_shouldReturn401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/notifications/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"device-token-123\"}"))
        .andExpect(status().isUnauthorized());

    verify(notificationService, never()).saveToken(any(), any());
  }

  private User buildActiveUser(String authSub) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Customer Test");
    user.setEmail("customer@ragro.com.br");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(true);
    user.setAuthSub(authSub);
    return user;
  }
}
