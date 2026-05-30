package br.com.ragro.controller;

import br.com.ragro.controller.response.NotificationResponse;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.UnreadCountResponse;
import br.com.ragro.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers/me/notifications")
@RequiredArgsConstructor
@Tag(
    name = "Customer Notifications",
    description = "Notification operations for authenticated customers")
public class CustomerNotificationController {

  private final NotificationService notificationService;

  @GetMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "List my notifications",
      description = "Returns the authenticated customer's notifications ordered by createdAt desc.")
  public ResponseEntity<PaginatedResponse<NotificationResponse>> getMyNotifications(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        notificationService.getMyCustomerNotifications(jwt, PageRequest.of(page, size)));
  }

  @GetMapping("/unread-count")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Get unread notification count",
      description = "Returns the number of unread notifications for the authenticated customer.")
  public ResponseEntity<UnreadCountResponse> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(notificationService.getMyCustomerUnreadCount(jwt));
  }

  @PatchMapping("/{notificationId}/read")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Mark notification as read",
      description = "Marks one notification as read for the authenticated customer.")
  public ResponseEntity<NotificationResponse> markAsRead(
      @PathVariable UUID notificationId, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(
        notificationService.markMyCustomerNotificationAsRead(notificationId, jwt));
  }

  @PatchMapping("/read-all")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Mark all notifications as read",
      description = "Marks all unread notifications as read for the authenticated customer.")
  public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
    notificationService.markAllMyCustomerNotificationsAsRead(jwt);
    return ResponseEntity.noContent().build();
  }
}
