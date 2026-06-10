package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.response.NotificationResponse;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.UnreadCountResponse;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.Notification;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.NotificationType;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private UserService userService;

  @InjectMocks private NotificationService notificationService;

  private User customerUser;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    customerUser = new User();
    customerUser.setId(UUID.randomUUID());
    customerUser.setType(TypeUser.CUSTOMER);
    customerUser.setName("Customer Test");

    jwt = Jwt.withTokenValue("token").header("alg", "RS256").claim("sub", "customer-sub").build();
  }

  @Test
  void getMyCustomerNotifications_shouldReturnPaginatedNotifications() {
    Notification notification = buildNotification(customerUser, NotificationType.ORDER_CONFIRMED);
    Page<Notification> page =
        new PageImpl<>(java.util.List.of(notification), PageRequest.of(0, 20), 1);

    when(userService.getAuthenticatedUser(jwt)).thenReturn(customerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(notificationRepository.findByUserIdOrderByCreatedAtDesc(
            customerUser.getId(), PageRequest.of(0, 20)))
        .thenReturn(page);

    PaginatedResponse<NotificationResponse> result =
        notificationService.getMyCustomerNotifications(jwt, PageRequest.of(0, 20));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getTitle()).isEqualTo("Pedido aceito");
  }

  @Test
  void getMyCustomerUnreadCount_shouldReturnRepositoryCount() {
    when(userService.getAuthenticatedUser(jwt)).thenReturn(customerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(notificationRepository.countByUserIdAndReadFalse(customerUser.getId())).thenReturn(3L);

    UnreadCountResponse result = notificationService.getMyCustomerUnreadCount(jwt);

    assertThat(result.getCount()).isEqualTo(3L);
  }

  @Test
  void markMyCustomerNotificationAsRead_shouldMarkUnreadNotification() {
    Notification notification = buildNotification(customerUser, NotificationType.ORDER_IN_DELIVERY);
    notification.setRead(false);

    when(userService.getAuthenticatedUser(jwt)).thenReturn(customerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(notificationRepository.findByIdAndUserId(notification.getId(), customerUser.getId()))
        .thenReturn(Optional.of(notification));

    NotificationResponse result =
        notificationService.markMyCustomerNotificationAsRead(notification.getId(), jwt);

    assertThat(result.isRead()).isTrue();
    assertThat(notification.getReadAt()).isNotNull();
    verify(notificationRepository).save(notification);
  }

  @Test
  void markMyCustomerNotificationAsRead_shouldThrowNotFoundWhenNotificationDoesNotExist() {
    UUID notificationId = UUID.randomUUID();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(customerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(notificationRepository.findByIdAndUserId(notificationId, customerUser.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> notificationService.markMyCustomerNotificationAsRead(notificationId, jwt))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Notificação não encontrada");
  }

  @Test
  void markAllMyCustomerNotificationsAsRead_shouldCallBulkUpdate() {
    when(userService.getAuthenticatedUser(jwt)).thenReturn(customerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });

    notificationService.markAllMyCustomerNotificationsAsRead(jwt);

    verify(notificationRepository)
        .markAllAsReadByUserId(
            org.mockito.ArgumentMatchers.eq(customerUser.getId()), any(OffsetDateTime.class));
  }

  @Test
  void createCustomerOrderAcceptedNotification_shouldPersistNotification() {
    Order order = buildOrder(customerUser);
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

    notificationService.createCustomerOrderAcceptedNotification(order);

    verify(notificationRepository).save(captor.capture());
    Notification saved = captor.getValue();
    assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CONFIRMED);
    assertThat(saved.getTitle()).isEqualTo("Pedido aceito");
    assertThat(saved.getReferenceId()).isEqualTo(order.getId());
    assertThat(saved.getUser()).isEqualTo(customerUser);
  }

  @Test
  void getMyCustomerNotifications_shouldRejectNonCustomer() {
    User farmerUser = new User();
    farmerUser.setId(UUID.randomUUID());
    farmerUser.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });

    assertThatThrownBy(
            () -> notificationService.getMyCustomerNotifications(jwt, PageRequest.of(0, 20)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("consumidores");
  }

  private Notification buildNotification(User user, NotificationType type) {
    Notification notification = new Notification();
    notification.setId(UUID.randomUUID());
    notification.setUser(user);
    notification.setTitle("Pedido aceito");
    notification.setMessage("Seu pedido foi aceito.");
    notification.setType(type);
    notification.setCreatedAt(OffsetDateTime.now());
    return notification;
  }

  private Order buildOrder(User user) {
    Customer customer = new Customer();
    customer.setId(user.getId());
    customer.setUser(user);

    Producer producer = new Producer();
    producer.setId(UUID.randomUUID());

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(producer);
    return order;
  }
}
