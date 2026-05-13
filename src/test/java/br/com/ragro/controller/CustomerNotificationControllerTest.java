package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import br.com.ragro.exception.NotFoundException;
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

@WebMvcTest(CustomerNotificationController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class CustomerNotificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private NotificationService notificationService;
  @MockBean private UserRepository userRepository;

  @Test
  void getMyNotifications_shouldReturn200() throws Exception {
    String sub = "customer-sub";
    UUID notificationId = UUID.randomUUID();
    NotificationResponse response = NotificationResponse.builder()
        .id(notificationId)
        .title("Pedido aceito")
        .message("Seu pedido foi aceito pelo produtor.")
        .type(NotificationType.ORDER_CONFIRMED)
        .referenceType(NotificationReferenceType.ORDER)
        .referenceId(UUID.randomUUID())
        .read(false)
        .createdAt(OffsetDateTime.parse("2026-05-13T12:00:00Z"))
        .build();

    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomerUser(sub, true)));
    when(notificationService.getMyCustomerNotifications(any(), any()))
        .thenReturn(
            PaginatedResponse.<NotificationResponse>builder()
                .content(List.of(response))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build());

    mockMvc.perform(
            get("/customers/me/notifications")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(notificationId.toString()))
        .andExpect(jsonPath("$.content[0].title").value("Pedido aceito"));
  }

  @Test
  void getUnreadCount_shouldReturn200() throws Exception {
    String sub = "customer-sub";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomerUser(sub, true)));
    when(notificationService.getMyCustomerUnreadCount(any())).thenReturn(new UnreadCountResponse(4));

    mockMvc.perform(
            get("/customers/me/notifications/unread-count")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(4));
  }

  @Test
  void markAsRead_shouldReturn200() throws Exception {
    String sub = "customer-sub";
    UUID notificationId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomerUser(sub, true)));
    when(notificationService.markMyCustomerNotificationAsRead(eq(notificationId), any()))
        .thenReturn(
            NotificationResponse.builder()
                .id(notificationId)
                .title("Seu pedido chegou")
                .message("Seu pedido foi entregue.")
                .type(NotificationType.ORDER_DELIVERED)
                .read(true)
                .createdAt(OffsetDateTime.parse("2026-05-13T12:00:00Z"))
                .readAt(OffsetDateTime.parse("2026-05-13T12:30:00Z"))
                .build());

    mockMvc.perform(
            patch("/customers/me/notifications/{notificationId}/read", notificationId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.read").value(true));
  }

  @Test
  void markAsRead_shouldReturn404() throws Exception {
    String sub = "customer-sub";
    UUID notificationId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomerUser(sub, true)));
    when(notificationService.markMyCustomerNotificationAsRead(eq(notificationId), any()))
        .thenThrow(new NotFoundException("Notificação não encontrada"));

    mockMvc.perform(
            patch("/customers/me/notifications/{notificationId}/read", notificationId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Notificação não encontrada"));
  }

  @Test
  void markAllAsRead_shouldReturn204() throws Exception {
    String sub = "customer-sub";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomerUser(sub, true)));

    mockMvc.perform(
            patch("/customers/me/notifications/read-all")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isNoContent());
  }

  private User buildCustomerUser(String authSub, boolean active) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Customer Test");
    user.setEmail("customer@ragro.com.br");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(active);
    user.setAuthSub(authSub);
    return user;
  }
}
