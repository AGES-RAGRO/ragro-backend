package br.com.ragro.mapper;

import br.com.ragro.controller.response.NotificationResponse;
import br.com.ragro.domain.Notification;

public final class NotificationMapper {

  private NotificationMapper() {}

  public static NotificationResponse toResponse(Notification notification) {
    if (notification == null) {
      return null;
    }

    return NotificationResponse.builder()
        .id(notification.getId())
        .title(notification.getTitle())
        .message(notification.getMessage())
        .type(notification.getType())
        .referenceType(notification.getReferenceType())
        .referenceId(notification.getReferenceId())
        .read(notification.isRead())
        .createdAt(notification.getCreatedAt())
        .readAt(notification.getReadAt())
        .build();
  }
}
