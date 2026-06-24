package br.com.ragro.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.TrackingService;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

@ExtendWith(MockitoExtension.class)
class TrackingChannelInterceptorTest {

  @Mock private UserRepository userRepository;
  @Mock private TrackingService trackingService;

  private TrackingChannelInterceptor interceptor;
  private MessageChannel channel;

  @BeforeEach
  void setUp() {
    interceptor = new TrackingChannelInterceptor(userRepository, trackingService);
    channel = mock(MessageChannel.class);
  }

  // ---- helpers ---------------------------------------------------------------

  private Message<byte[]> stompMessage(
      StompCommand command,
      Principal principal,
      String destination,
      Map<String, Object> sessionAttributes) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    if (principal != null) {
      accessor.setUser(principal);
    }
    if (sessionAttributes != null) {
      accessor.setSessionAttributes(sessionAttributes);
    }
    if (destination != null) {
      accessor.setDestination(destination);
    }
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private User user(UUID id, boolean active, TypeUser type, String authSub) {
    User u = new User();
    u.setId(id);
    u.setActive(active);
    u.setType(type);
    u.setAuthSub(authSub);
    return u;
  }

  private Map<String, Object> sessionWith(UUID userId, TypeUser type) {
    Map<String, Object> attrs = new HashMap<>();
    if (userId != null) {
      attrs.put(TrackingChannelInterceptor.SESSION_USER_ID, userId);
    }
    if (type != null) {
      attrs.put(TrackingChannelInterceptor.SESSION_USER_TYPE, type);
    }
    return attrs;
  }

  // ---- command == null / default branch -------------------------------------

  @Test
  void preSend_nullCommand_returnsSameMessage() {
    Message<String> message = new GenericMessage<>("payload");

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isSameAs(message);
  }

  @Test
  void preSend_unhandledCommand_returnsSameMessage() {
    Message<byte[]> message = stompMessage(StompCommand.DISCONNECT, null, null, null);

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isSameAs(message);
  }

  // ---- CONNECT --------------------------------------------------------------

  @Test
  void preSend_connectWithoutPrincipal_returnsNull() {
    Message<byte[]> message =
        stompMessage(StompCommand.CONNECT, null, null, new HashMap<>());

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
  }

  @Test
  void preSend_connectUserNotFound_returnsNull() {
    Principal principal = () -> "sub-unknown";
    when(userRepository.findByAuthSub("sub-unknown")).thenReturn(Optional.empty());
    Message<byte[]> message =
        stompMessage(StompCommand.CONNECT, principal, null, new HashMap<>());

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
    verify(userRepository).findByAuthSub("sub-unknown");
  }

  @Test
  void preSend_connectInactiveUser_returnsNull() {
    Principal principal = () -> "sub-inactive";
    User inactive = user(UUID.randomUUID(), false, TypeUser.FARMER, "sub-inactive");
    when(userRepository.findByAuthSub("sub-inactive")).thenReturn(Optional.of(inactive));
    Map<String, Object> attrs = new HashMap<>();
    Message<byte[]> message = stompMessage(StompCommand.CONNECT, principal, null, attrs);

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
    assertThat(attrs).doesNotContainKey(TrackingChannelInterceptor.SESSION_USER_ID);
    assertThat(attrs).doesNotContainKey(TrackingChannelInterceptor.SESSION_USER_TYPE);
  }

  @Test
  void preSend_connectActiveUser_returnsMessageAndStoresSessionAttributes() {
    Principal principal = () -> "sub-active";
    UUID userId = UUID.randomUUID();
    User active = user(userId, true, TypeUser.CUSTOMER, "sub-active");
    when(userRepository.findByAuthSub("sub-active")).thenReturn(Optional.of(active));
    Map<String, Object> attrs = new HashMap<>();
    Message<byte[]> message = stompMessage(StompCommand.CONNECT, principal, null, attrs);

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isSameAs(message);
    assertThat(attrs.get(TrackingChannelInterceptor.SESSION_USER_ID)).isEqualTo(userId);
    assertThat(attrs.get(TrackingChannelInterceptor.SESSION_USER_TYPE))
        .isEqualTo(TypeUser.CUSTOMER);
    verify(userRepository).findByAuthSub("sub-active");
  }

  @Test
  void preSend_connectActiveUserNullSessionAttributes_returnsMessage() {
    Principal principal = () -> "sub-no-attrs";
    User active = user(UUID.randomUUID(), true, TypeUser.FARMER, "sub-no-attrs");
    when(userRepository.findByAuthSub("sub-no-attrs")).thenReturn(Optional.of(active));
    // No session attributes set -> getSessionAttributes() is null inside handleConnect.
    Message<byte[]> message = stompMessage(StompCommand.CONNECT, principal, null, null);

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isSameAs(message);
  }

  // ---- SUBSCRIBE ------------------------------------------------------------

  @Test
  void preSend_subscribeWrongPrefix_returnsNull() {
    UUID userId = UUID.randomUUID();
    Message<byte[]> message =
        stompMessage(
            StompCommand.SUBSCRIBE,
            null,
            "/queue/routes/" + UUID.randomUUID(),
            sessionWith(userId, TypeUser.CUSTOMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
  }

  @Test
  void preSend_subscribeNullDestination_returnsNull() {
    UUID userId = UUID.randomUUID();
    Message<byte[]> message =
        stompMessage(StompCommand.SUBSCRIBE, null, null, sessionWith(userId, TypeUser.CUSTOMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
  }

  @Test
  void preSend_subscribeMissingSessionUserId_returnsNull() {
    UUID routeId = UUID.randomUUID();
    Message<byte[]> message =
        stompMessage(
            StompCommand.SUBSCRIBE,
            null,
            "/topic/routes/" + routeId,
            sessionWith(null, TypeUser.CUSTOMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
  }

  @Test
  void preSend_subscribeMissingSessionType_returnsNull() {
    UUID routeId = UUID.randomUUID();
    Message<byte[]> message =
        stompMessage(
            StompCommand.SUBSCRIBE,
            null,
            "/topic/routes/" + routeId,
            sessionWith(UUID.randomUUID(), null));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
  }

  @Test
  void preSend_subscribeMalformedRouteId_returnsNull() {
    Message<byte[]> message =
        stompMessage(
            StompCommand.SUBSCRIBE,
            null,
            "/topic/routes/not-a-uuid",
            sessionWith(UUID.randomUUID(), TypeUser.CUSTOMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
  }

  @Test
  void preSend_subscribeCanSubscribeFalse_returnsNull() {
    UUID routeId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(trackingService.canSubscribe(routeId, userId, TypeUser.CUSTOMER)).thenReturn(false);
    Message<byte[]> message =
        stompMessage(
            StompCommand.SUBSCRIBE,
            null,
            "/topic/routes/" + routeId,
            sessionWith(userId, TypeUser.CUSTOMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
    verify(trackingService).canSubscribe(routeId, userId, TypeUser.CUSTOMER);
  }

  @Test
  void preSend_subscribeCanSubscribeTrue_returnsMessage() {
    UUID routeId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(trackingService.canSubscribe(routeId, userId, TypeUser.CUSTOMER)).thenReturn(true);
    Message<byte[]> message =
        stompMessage(
            StompCommand.SUBSCRIBE,
            null,
            "/topic/routes/" + routeId,
            sessionWith(userId, TypeUser.CUSTOMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isSameAs(message);
    verify(trackingService).canSubscribe(routeId, userId, TypeUser.CUSTOMER);
  }

  @Test
  void preSend_subscribeRouteIdWithTrailingSegment_returnsMessage() {
    UUID routeId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(trackingService.canSubscribe(routeId, userId, TypeUser.CUSTOMER)).thenReturn(true);
    Message<byte[]> message =
        stompMessage(
            StompCommand.SUBSCRIBE,
            null,
            "/topic/routes/" + routeId + "/extra",
            sessionWith(userId, TypeUser.CUSTOMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isSameAs(message);
    verify(trackingService).canSubscribe(routeId, userId, TypeUser.CUSTOMER);
  }

  // ---- SEND -----------------------------------------------------------------

  @Test
  void preSend_sendWrongPrefix_returnsNull() {
    Message<byte[]> message =
        stompMessage(
            StompCommand.SEND,
            null,
            "/topic/routes/" + UUID.randomUUID() + "/position",
            sessionWith(UUID.randomUUID(), TypeUser.FARMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
  }

  @Test
  void preSend_sendNullDestination_returnsNull() {
    Message<byte[]> message =
        stompMessage(StompCommand.SEND, null, null, sessionWith(UUID.randomUUID(), TypeUser.FARMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
  }

  @Test
  void preSend_sendNonFarmerType_returnsNull() {
    UUID routeId = UUID.randomUUID();
    Message<byte[]> message =
        stompMessage(
            StompCommand.SEND,
            null,
            "/app/routes/" + routeId + "/position",
            sessionWith(UUID.randomUUID(), TypeUser.CUSTOMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
  }

  @Test
  void preSend_sendMalformedRouteId_returnsNull() {
    Message<byte[]> message =
        stompMessage(
            StompCommand.SEND,
            null,
            "/app/routes/not-a-uuid/position",
            sessionWith(UUID.randomUUID(), TypeUser.FARMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
  }

  @Test
  void preSend_sendFarmerValidDestination_returnsMessage() {
    UUID routeId = UUID.randomUUID();
    Message<byte[]> message =
        stompMessage(
            StompCommand.SEND,
            null,
            "/app/routes/" + routeId + "/position",
            sessionWith(UUID.randomUUID(), TypeUser.FARMER));

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isSameAs(message);
  }

  // ---- static helpers -------------------------------------------------------

  @Test
  void sessionUserId_nullAttributes_returnsNull() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    // No session attributes set.
    assertThat(TrackingChannelInterceptor.sessionUserId(accessor)).isNull();
  }

  @Test
  void sessionUserId_withAttributes_returnsStoredValue() {
    UUID userId = UUID.randomUUID();
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setSessionAttributes(sessionWith(userId, TypeUser.FARMER));

    assertThat(TrackingChannelInterceptor.sessionUserId(accessor)).isEqualTo(userId);
  }

  @Test
  void sessionUserType_nullAttributes_returnsNull() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    assertThat(TrackingChannelInterceptor.sessionUserType(accessor)).isNull();
  }

  @Test
  void sessionUserType_withAttributes_returnsStoredValue() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setSessionAttributes(sessionWith(UUID.randomUUID(), TypeUser.ADMIN));

    assertThat(TrackingChannelInterceptor.sessionUserType(accessor)).isEqualTo(TypeUser.ADMIN);
  }
}
