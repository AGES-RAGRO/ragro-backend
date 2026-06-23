package br.com.ragro.controller;

import br.com.ragro.config.TrackingChannelInterceptor;
import br.com.ragro.controller.request.TrackingPositionMessage;
import br.com.ragro.service.TrackingService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Position ingestion via STOMP: producer sends {@code SEND /app/routes/{routeId}/position}; each
 * accepted ping is rebroadcast on {@code /topic/routes/{routeId}} to authorized subscribers.
 */
@Controller
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ragro.tracking.enabled", havingValue = "true")
public class TrackingWsController {

  private final TrackingService trackingService;
  private final SimpMessagingTemplate messagingTemplate;

  @MessageMapping("/routes/{routeId}/position")
  public void position(
      @DestinationVariable UUID routeId,
      @Payload TrackingPositionMessage message,
      SimpMessageHeaderAccessor accessor) {
    Map<String, Object> attributes = accessor.getSessionAttributes();
    UUID userId =
        attributes != null
            ? (UUID) attributes.get(TrackingChannelInterceptor.SESSION_USER_ID)
            : null;
    if (userId == null) {
      return;
    }
    trackingService
        .ingestPosition(routeId, userId, message)
        .ifPresent(
            broadcast ->
                messagingTemplate.convertAndSend("/topic/routes/" + routeId, broadcast));
  }
}
