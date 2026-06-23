package br.com.ragro.config;

import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.TrackingService;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Fine-grained authorization for the tracking STOMP channel:
 *
 * <ul>
 *   <li><b>CONNECT</b>: resolves user from handshake Principal (JWT sub), requires active account,
 *       caches id/type in session attributes (avoids a query per message).
 *   <li><b>SUBSCRIBE</b> {@code /topic/routes/{routeId}}: only the route's producer or a delivery's
 *       customer (privacy).
 *   <li><b>SEND</b> {@code /app/routes/{routeId}/position}: producers only (route ownership + rate
 *       limit checked in TrackingService).
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ragro.tracking.enabled", havingValue = "true")
public class TrackingChannelInterceptor implements ChannelInterceptor {

  private static final Logger log = LoggerFactory.getLogger(TrackingChannelInterceptor.class);

  public static final String SESSION_USER_ID = "ragro.userId";
  public static final String SESSION_USER_TYPE = "ragro.userType";

  private static final String TOPIC_PREFIX = "/topic/routes/";
  private static final String APP_PREFIX = "/app/routes/";

  private final UserRepository userRepository;
  private final TrackingService trackingService;

  public TrackingChannelInterceptor(
      UserRepository userRepository, TrackingService trackingService) {
    this.userRepository = userRepository;
    this.trackingService = trackingService;
  }

  @Override
  public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    StompCommand command = accessor.getCommand();
    if (command == null) {
      return message;
    }

    return switch (command) {
      case CONNECT -> handleConnect(message, accessor);
      case SUBSCRIBE -> handleSubscribe(message, accessor);
      case SEND -> handleSend(message, accessor);
      default -> message;
    };
  }

  private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
    Principal principal = accessor.getUser();
    if (principal == null) {
      log.warn("STOMP CONNECT rejected: no authenticated principal in handshake");
      return null;
    }
    // Principal.getName() = JWT sub (Keycloak); resolve the domain user once.
    User user = userRepository.findByAuthSub(principal.getName()).orElse(null);
    if (user == null || !user.isActive()) {
      log.warn("STOMP CONNECT rejected: unknown or inactive user");
      return null;
    }
    Map<String, Object> attributes = accessor.getSessionAttributes();
    if (attributes != null) {
      attributes.put(SESSION_USER_ID, user.getId());
      attributes.put(SESSION_USER_TYPE, user.getType());
    }
    return message;
  }

  private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null || !destination.startsWith("/topic/")) {
      return null;
    }
    UUID routeId = extractRouteId(destination, TOPIC_PREFIX);
    UUID userId = sessionUserId(accessor);
    TypeUser type = sessionUserType(accessor);
    if (routeId == null || userId == null || type == null) {
      return null;
    }
    if (!trackingService.canSubscribe(routeId, userId, type)) {
      log.warn("STOMP SUBSCRIBE rejected for route {}: user not related to route", routeId);
      return null;
    }
    return message;
  }

  private Message<?> handleSend(Message<?> message, StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null || !destination.startsWith("/app/")) {
      return null;
    }
    UUID routeId = extractRouteId(destination, APP_PREFIX);
    TypeUser type = sessionUserType(accessor);
    if (routeId == null || type != TypeUser.FARMER) {
      return null;
    }
    return message;
  }

  /** Extracts the UUID from "/[prefix]/{routeId}[/...]"; null for a malformed destination. */
  private UUID extractRouteId(String destination, String prefix) {
    if (!destination.startsWith(prefix)) {
      return null;
    }
    String rest = destination.substring(prefix.length());
    int slash = rest.indexOf('/');
    String id = slash >= 0 ? rest.substring(0, slash) : rest;
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static UUID sessionUserId(StompHeaderAccessor accessor) {
    Map<String, Object> attributes = accessor.getSessionAttributes();
    return attributes != null ? (UUID) attributes.get(SESSION_USER_ID) : null;
  }

  public static TypeUser sessionUserType(StompHeaderAccessor accessor) {
    Map<String, Object> attributes = accessor.getSessionAttributes();
    return attributes != null ? (TypeUser) attributes.get(SESSION_USER_TYPE) : null;
  }
}
