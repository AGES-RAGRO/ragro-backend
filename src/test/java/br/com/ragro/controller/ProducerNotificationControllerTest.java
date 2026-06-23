package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.NotificationResponse;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.UnreadCountResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.NotificationReferenceType;
import br.com.ragro.domain.enums.NotificationType;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.NotificationService;
import java.time.OffsetDateTime;
import java.util.List;
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

@WebMvcTest(ProducerNotificationController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class ProducerNotificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private NotificationService notificationService;
  @MockBean private UserRepository userRepository;

  @Test
  void getMyNotifications_shouldReturn200() throws Exception {
    String sub = "farmer-sub";
    NotificationResponse response =
        NotificationResponse.builder()
            .id(UUID.randomUUID())
            .title("Novo pedido recebido")
            .message("Você recebeu um novo pedido. Pedido #abc.")
            .type(NotificationType.NEW_ORDER)
            .referenceType(NotificationReferenceType.ORDER)
            .referenceId(UUID.randomUUID())
            .read(false)
            .createdAt(OffsetDateTime.parse("2026-06-17T12:00:00Z"))
            .build();

    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildFarmer(sub)));
    when(notificationService.getMyProducerNotifications(any(), any()))
        .thenReturn(
            PaginatedResponse.<NotificationResponse>builder()
                .content(List.of(response))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build());

    mockMvc
        .perform(
            get("/producers/me/notifications")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].type").value("NEW_ORDER"));
  }

  @Test
  void getUnreadCount_shouldReturn200() throws Exception {
    String sub = "farmer-sub";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildFarmer(sub)));
    when(notificationService.getMyProducerUnreadCount(any()))
        .thenReturn(new UnreadCountResponse(2));

    mockMvc
        .perform(
            get("/producers/me/notifications/unread-count")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(2));
  }

  @Test
  void markAllAsRead_shouldReturn204() throws Exception {
    String sub = "farmer-sub";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildFarmer(sub)));

    mockMvc
        .perform(
            patch("/producers/me/notifications/read-all")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isNoContent());

    verify(notificationService).markAllMyProducerNotificationsAsRead(any());
  }

  @Test
  void getMyNotifications_shouldReturn403ForCustomer() throws Exception {
    String sub = "customer-sub";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildFarmer(sub)));

    mockMvc
        .perform(
            get("/producers/me/notifications")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
  }

  private User buildFarmer(String authSub) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Producer Test");
    user.setEmail("farmer@ragro.com.br");
    user.setType(TypeUser.FARMER);
    user.setActive(true);
    user.setAuthSub(authSub);
    return user;
  }
}
