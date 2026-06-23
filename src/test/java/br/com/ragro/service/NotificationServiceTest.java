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
import br.com.ragro.domain.FcmToken;
import br.com.ragro.domain.Notification;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.NotificationType;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.event.OrderPushNotificationEvent;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.FcmTokenRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private FcmTokenRepository fcmTokenRepository;
  @Mock private UserService userService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

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
    when(notificationRepository.countByUserIdAndReadFalse(customerUser.getId())).thenReturn(3L);

    UnreadCountResponse result = notificationService.getMyCustomerUnreadCount(jwt);

    assertThat(result.getCount()).isEqualTo(3L);
  }

  @Test
  void markMyCustomerNotificationAsRead_shouldMarkUnreadNotification() {
    Notification notification = buildNotification(customerUser, NotificationType.ORDER_IN_DELIVERY);
    notification.setRead(false);

    when(userService.getAuthenticatedUser(jwt)).thenReturn(customerUser);
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

    notificationService.markAllMyCustomerNotificationsAsRead(jwt);

    verify(notificationRepository)
        .markAllAsReadByUserId(
            org.mockito.ArgumentMatchers.eq(customerUser.getId()), any(OffsetDateTime.class));
  }

  @Test
  void createCustomerOrderAcceptedNotification_shouldPersistNotificationAndPublishPushEvent() {
    Order order = buildOrder(customerUser);
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    ArgumentCaptor<OrderPushNotificationEvent> eventCaptor =
        ArgumentCaptor.forClass(OrderPushNotificationEvent.class);

    notificationService.createCustomerOrderAcceptedNotification(order);

    verify(notificationRepository).save(captor.capture());
    Notification saved = captor.getValue();
    assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CONFIRMED);
    assertThat(saved.getTitle()).isEqualTo("Pedido aceito");
    assertThat(saved.getReferenceId()).isEqualTo(order.getId());
    assertThat(saved.getUser()).isEqualTo(customerUser);

    verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
    OrderPushNotificationEvent event = eventCaptor.getValue();
    assertThat(event.userId()).isEqualTo(customerUser.getId());
    assertThat(event.title()).isEqualTo("Pedido aceito");
    assertThat(event.body()).isEqualTo("Seu pedido foi aceito pelo produtor.");
    assertThat(event.referenceId()).isEqualTo(order.getId());
    assertThat(event.type()).isEqualTo(NotificationType.ORDER_CONFIRMED);
  }

  @Test
  void createCustomerOrderInDeliveryNotification_shouldIncludeConfirmationCode() {
    Order order = buildOrder(customerUser);
    order.setConfirmationCode("1234");
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    ArgumentCaptor<OrderPushNotificationEvent> eventCaptor =
        ArgumentCaptor.forClass(OrderPushNotificationEvent.class);

    notificationService.createCustomerOrderInDeliveryNotification(order);

    verify(notificationRepository).save(captor.capture());
    assertThat(captor.getValue().getMessage()).contains("1234").doesNotContain("null");

    verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().body())
        .isEqualTo("Informe o código 1234 ao produtor para confirmar a entrega.");
  }

  @Test
  void createCustomerOrderInDeliveryNotification_shouldFallBackWhenCodeIsNull() {
    Order order = buildOrder(customerUser); // confirmationCode null by default
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    ArgumentCaptor<OrderPushNotificationEvent> eventCaptor =
        ArgumentCaptor.forClass(OrderPushNotificationEvent.class);

    notificationService.createCustomerOrderInDeliveryNotification(order);

    verify(notificationRepository).save(captor.capture());
    assertThat(captor.getValue().getMessage()).doesNotContain("null");

    verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().body()).isEqualTo("Seu pedido saiu para entrega.");
  }

  @Test
  void saveToken_shouldTrimAndPersistTokenForAuthenticatedUser() {
    when(userService.getAuthenticatedUser(jwt)).thenReturn(customerUser);
    when(fcmTokenRepository.findByToken("device-token")).thenReturn(Optional.empty());
    ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);

    notificationService.saveToken(jwt, "  device-token  ");

    verify(fcmTokenRepository).save(captor.capture());
    FcmToken saved = captor.getValue();
    assertThat(saved.getToken()).isEqualTo("device-token");
    assertThat(saved.getUser()).isEqualTo(customerUser);
  }

  @Test
  void getMyCustomerNotifications_shouldRejectNonCustomer() {
    User farmerUser = new User();
    farmerUser.setId(UUID.randomUUID());
    farmerUser.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmerUser);

    assertThatThrownBy(
            () -> notificationService.getMyCustomerNotifications(jwt, PageRequest.of(0, 20)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("consumidores");
  }

  @Test
  void createProducerNewOrderNotification_shouldPersistAndPublishForFarmer() {
    Order order = buildOrder(customerUser);
    User farmerUser = order.getFarmer().getUser();
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    ArgumentCaptor<OrderPushNotificationEvent> eventCaptor =
        ArgumentCaptor.forClass(OrderPushNotificationEvent.class);

    notificationService.createProducerNewOrderNotification(order);

    verify(notificationRepository).save(captor.capture());
    Notification saved = captor.getValue();
    assertThat(saved.getType()).isEqualTo(NotificationType.NEW_ORDER);
    assertThat(saved.getTitle()).isEqualTo("Novo pedido recebido");
    assertThat(saved.getUser()).isEqualTo(farmerUser);

    verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().userId()).isEqualTo(farmerUser.getId());
    assertThat(eventCaptor.getValue().type()).isEqualTo(NotificationType.NEW_ORDER);
  }

  @Test
  void createProducerOrderCancelledByCustomerNotification_shouldPersistAndPublishForFarmer() {
    Order order = buildOrder(customerUser);
    User farmerUser = order.getFarmer().getUser();
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

    notificationService.createProducerOrderCancelledByCustomerNotification(order);

    verify(notificationRepository).save(captor.capture());
    assertThat(captor.getValue().getType()).isEqualTo(NotificationType.ORDER_CANCELLED_BY_CUSTOMER);
    assertThat(captor.getValue().getUser()).isEqualTo(farmerUser);
    verify(applicationEventPublisher)
        .publishEvent(
            org.mockito.ArgumentMatchers.argThat(
                (OrderPushNotificationEvent e) ->
                    e.userId().equals(farmerUser.getId())
                        && e.type() == NotificationType.ORDER_CANCELLED_BY_CUSTOMER));
  }

  @Test
  void getMyProducerNotifications_shouldRejectNonFarmer() {
    when(userService.getAuthenticatedUser(jwt)).thenReturn(customerUser);

    assertThatThrownBy(
            () -> notificationService.getMyProducerNotifications(jwt, PageRequest.of(0, 20)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("produtores");
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

    User farmerUser = new User();
    farmerUser.setId(UUID.randomUUID());
    farmerUser.setType(TypeUser.FARMER);
    Producer producer = new Producer();
    producer.setId(farmerUser.getId());
    producer.setUser(farmerUser);

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(producer);
    return order;
  }
}
