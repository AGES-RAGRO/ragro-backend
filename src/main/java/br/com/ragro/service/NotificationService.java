package br.com.ragro.service;

import br.com.ragro.controller.response.NotificationResponse;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.UnreadCountResponse;
import br.com.ragro.domain.FcmToken;
import br.com.ragro.domain.Notification;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.NotificationReferenceType;
import br.com.ragro.domain.enums.NotificationType;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.event.OrderPushNotificationEvent;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.NotificationMapper;
import br.com.ragro.repository.FcmTokenRepository;
import br.com.ragro.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final FcmTokenRepository fcmTokenRepository;
  private final UserService userService;
  private final ApplicationEventPublisher applicationEventPublisher;

  // ---------------------------------------------------------------------------
  // Customer read operations
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public PaginatedResponse<NotificationResponse> getMyCustomerNotifications(
      Jwt jwt, Pageable pageable) {
    return listFor(requireCustomer(jwt), pageable);
  }

  @Transactional(readOnly = true)
  public UnreadCountResponse getMyCustomerUnreadCount(Jwt jwt) {
    return unreadCountFor(requireCustomer(jwt));
  }

  @Transactional
  public NotificationResponse markMyCustomerNotificationAsRead(UUID notificationId, Jwt jwt) {
    return markAsReadFor(requireCustomer(jwt), notificationId);
  }

  @Transactional
  public void markAllMyCustomerNotificationsAsRead(Jwt jwt) {
    markAllAsReadFor(requireCustomer(jwt));
  }

  // ---------------------------------------------------------------------------
  // Producer read operations
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public PaginatedResponse<NotificationResponse> getMyProducerNotifications(
      Jwt jwt, Pageable pageable) {
    return listFor(requireFarmer(jwt), pageable);
  }

  @Transactional(readOnly = true)
  public UnreadCountResponse getMyProducerUnreadCount(Jwt jwt) {
    return unreadCountFor(requireFarmer(jwt));
  }

  @Transactional
  public NotificationResponse markMyProducerNotificationAsRead(UUID notificationId, Jwt jwt) {
    return markAsReadFor(requireFarmer(jwt), notificationId);
  }

  @Transactional
  public void markAllMyProducerNotificationsAsRead(Jwt jwt) {
    markAllAsReadFor(requireFarmer(jwt));
  }

  // ---------------------------------------------------------------------------
  // Shared read helpers (recipient-agnostic)
  // ---------------------------------------------------------------------------

  private PaginatedResponse<NotificationResponse> listFor(User user, Pageable pageable) {
    return PaginatedResponse.of(
        notificationRepository
            .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
            .map(NotificationMapper::toResponse));
  }

  private UnreadCountResponse unreadCountFor(User user) {
    return new UnreadCountResponse(notificationRepository.countByUserIdAndReadFalse(user.getId()));
  }

  private NotificationResponse markAsReadFor(User user, UUID notificationId) {
    Notification notification =
        notificationRepository
            .findByIdAndUserId(notificationId, user.getId())
            .orElseThrow(() -> new NotFoundException("Notificação não encontrada"));

    if (!notification.isRead()) {
      notification.setRead(true);
      notification.setReadAt(OffsetDateTime.now());
      notificationRepository.save(notification);
    }

    return NotificationMapper.toResponse(notification);
  }

  private void markAllAsReadFor(User user) {
    notificationRepository.markAllAsReadByUserId(user.getId(), OffsetDateTime.now());
  }

  // ---------------------------------------------------------------------------
  // FCM token registration
  // ---------------------------------------------------------------------------

  @Transactional
  public void saveToken(Jwt jwt, String token) {
    User user = userService.getAuthenticatedUser(jwt);

    String normalizedToken = token.trim();
    FcmToken fcmToken = fcmTokenRepository.findByToken(normalizedToken).orElseGet(FcmToken::new);
    fcmToken.setUser(user);
    fcmToken.setToken(normalizedToken);
    fcmTokenRepository.save(fcmToken);
  }

  // ---------------------------------------------------------------------------
  // Customer-facing order notifications
  // ---------------------------------------------------------------------------

  @Transactional
  public void createCustomerOrderAcceptedNotification(Order order) {
    createOrderNotification(
        order,
        order.getCustomer().getUser(),
        NotificationType.ORDER_CONFIRMED,
        "Pedido aceito",
        "Seu pedido foi aceito pelo produtor.");
  }

  @Transactional
  public void createCustomerOrderInDeliveryNotification(Order order) {
    createOrderNotification(
        order,
        order.getCustomer().getUser(),
        NotificationType.ORDER_IN_DELIVERY,
        "Pedido saiu para entrega",
        "Seu pedido saiu para entrega.");
  }

  @Transactional
  public void createCustomerOrderDeliveredNotification(Order order) {
    createOrderNotification(
        order,
        order.getCustomer().getUser(),
        NotificationType.ORDER_DELIVERED,
        "Seu pedido chegou",
        "Seu pedido foi entregue.");
  }

  @Transactional
  public void createCustomerOrderRefusedNotification(Order order) {
    createOrderNotification(
        order,
        order.getCustomer().getUser(),
        NotificationType.ORDER_REFUSED,
        "Pedido foi recusado",
        "O produtor recusou o seu pedido.");
  }

  // ---------------------------------------------------------------------------
  // Producer-facing order notifications
  // ---------------------------------------------------------------------------

  @Transactional
  public void createProducerNewOrderNotification(Order order) {
    createOrderNotification(
        order,
        order.getFarmer().getUser(),
        NotificationType.NEW_ORDER,
        "Novo pedido recebido",
        "Você recebeu um novo pedido.");
  }

  @Transactional
  public void createProducerOrderCancelledByCustomerNotification(Order order) {
    createOrderNotification(
        order,
        order.getFarmer().getUser(),
        NotificationType.ORDER_CANCELLED_BY_CUSTOMER,
        "Pedido cancelado",
        "O cliente cancelou o pedido.");
  }

  private void createOrderNotification(
      Order order, User recipient, NotificationType type, String title, String baseMessage) {
    Notification notification = new Notification();
    notification.setUser(recipient);
    notification.setTitle(title);
    notification.setMessage(baseMessage + " Pedido #" + order.getId() + ".");
    notification.setType(type);
    notification.setReferenceType(NotificationReferenceType.ORDER);
    notification.setReferenceId(order.getId());
    notification.setRead(false);
    notificationRepository.save(notification);

    applicationEventPublisher.publishEvent(
        new OrderPushNotificationEvent(
            recipient.getId(), title, baseMessage, order.getId(), type, NotificationReferenceType.ORDER));
  }

  // ---------------------------------------------------------------------------
  // Producer-facing stock notifications
  // ---------------------------------------------------------------------------

  @Transactional
  public void createProducerLowStockNotification(Product product) {
    Producer farmer = product.getFarmer();
    String message =
        "O produto \""
            + product.getName()
            + "\" está com estoque baixo ("
            + product.getStockQuantity().stripTrailingZeros().toPlainString()
            + " unidades restantes).";

    Notification notification = new Notification();
    notification.setUser(farmer.getUser());
    notification.setTitle("Estoque baixo");
    notification.setMessage(message);
    notification.setType(NotificationType.LOW_STOCK);
    notification.setReferenceType(NotificationReferenceType.PRODUCT);
    notification.setReferenceId(product.getId());
    notification.setRead(false);
    notificationRepository.save(notification);

    applicationEventPublisher.publishEvent(
        new OrderPushNotificationEvent(
            farmer.getUser().getId(),
            "Estoque baixo",
            "O produto \"" + product.getName() + "\" está com estoque baixo.",
            product.getId(),
            NotificationType.LOW_STOCK,
            NotificationReferenceType.PRODUCT));
  }

  private User requireCustomer(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas consumidores podem acessar notificações");
    }
    return user;
  }

  private User requireFarmer(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.FARMER) {
      throw new ForbiddenException("Apenas produtores podem acessar notificações");
    }
    return user;
  }
}
