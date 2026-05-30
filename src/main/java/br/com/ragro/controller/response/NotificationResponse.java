package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.NotificationReferenceType;
import br.com.ragro.domain.enums.NotificationType;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationResponse {

  private UUID id;
  private String title;
  private String message;
  private NotificationType type;
  private NotificationReferenceType referenceType;
  private UUID referenceId;
  private boolean read;
  private OffsetDateTime createdAt;
  private OffsetDateTime readAt;
}
