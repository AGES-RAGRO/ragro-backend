package br.com.ragro.service;

import br.com.ragro.controller.response.NotificationResponse;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.UnreadCountResponse;
import br.com.ragro.domain.Notification;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.NotificationReferenceType;
import br.com.ragro.domain.enums.NotificationType;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.NotificationMapper;
import br.com.ragro.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final UserService userService;

  @Transactional(readOnly = true)
  public PaginatedResponse<NotificationResponse> getMyCustomerNotifications(
      Jwt jwt, Pageable pageable) {
    User user = requireCustomer(jwt);
    return PaginatedResponse.of(
        notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
            .map(NotificationMapper::toResponse));
  }

  @Transactional(readOnly = true)
  public UnreadCountResponse getMyCustomerUnreadCount(Jwt jwt) {
    User user = requireCustomer(jwt);
    return new UnreadCountResponse(notificationRepository.countByUserIdAndReadFalse(user.getId()));
  }

  @Transactional
  public NotificationResponse markMyCustomerNotificationAsRead(UUID notificationId, Jwt jwt) {
    User user = requireCustomer(jwt);
    Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
        .orElseThrow(() -> new NotFoundException("Notificação não encontrada"));

    if (!notification.isRead()) {
      notification.setRead(true);
      notification.setReadAt(OffsetDateTime.now());
      notificationRepository.save(notification);
    }

    return NotificationMapper.toResponse(notification);
  }

  @Transactional
  public void markAllMyCustomerNotificationsAsRead(Jwt jwt) {
    User user = requireCustomer(jwt);
    notificationRepository.markAllAsReadByUserId(user.getId(), OffsetDateTime.now());
  }

  @Transactional
  public void createCustomerOrderAcceptedNotification(Order order) {
    createOrderNotification(
        order,
        NotificationType.ORDER_CONFIRMED,
        "Pedido aceito",
        "Seu pedido foi aceito pelo produtor.");
  }

  @Transactional
  public void createCustomerOrderInDeliveryNotification(Order order) {
    createOrderNotification(
        order,
        NotificationType.ORDER_IN_DELIVERY,
        "Pedido saiu para entrega",
        "Seu pedido saiu para entrega.");
  }

  @Transactional
  public void createCustomerOrderDeliveredNotification(Order order) {
    createOrderNotification(
        order,
        NotificationType.ORDER_DELIVERED,
        "Seu pedido chegou",
        "Seu pedido foi entregue.");
  }

  @Transactional
  public void createCustomerOrderRefusedNotification(Order order) {
    createOrderNotification(
        order,
        NotificationType.ORDER_REFUSED,
        "Pedido foi recusado",
        "O produtor recusou o seu pedido.");
  }

  private void createOrderNotification(
      Order order,
      NotificationType type,
      String title,
      String baseMessage) {
    Notification notification = new Notification();
    notification.setUser(order.getCustomer().getUser());
    notification.setTitle(title);
    notification.setMessage(baseMessage + " Pedido #" + order.getId() + ".");
    notification.setType(type);
    notification.setReferenceType(NotificationReferenceType.ORDER);
    notification.setReferenceId(order.getId());
    notification.setRead(false);
    notificationRepository.save(notification);
  }

  private User requireCustomer(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas consumidores podem acessar notificações");
    }
    return user;
  }
}
