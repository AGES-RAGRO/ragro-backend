package br.com.ragro.service;

import br.com.ragro.event.OrderPushNotificationEvent;
import br.com.ragro.repository.FcmTokenRepository;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Delivers Firebase Cloud Messaging push notifications.
 *
 * <p>{@link #send} is invoked by {@link br.com.ragro.listener.PushNotificationListener} after the
 * originating order transaction commits, and runs {@code @Async} off the request thread, so a slow
 * FCM call never holds DB locks or stalls the HTTP response. When Firebase is disabled (no
 * service-account JSON) every send is a no-op. Tokens that FCM reports as permanently invalid are
 * pruned from the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

  /** FCM caps a single multicast at 500 tokens. */
  private static final int BATCH_SIZE = 500;

  /**
   * Codes that unambiguously mark a dead token (safe to prune). {@code INVALID_ARGUMENT} is
   * excluded because it is ambiguous: FCM also returns it for a malformed payload.
   */
  private static final Set<MessagingErrorCode> STALE_TOKEN_CODES =
      EnumSet.of(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.SENDER_ID_MISMATCH);

  private final FcmTokenRepository fcmTokenRepository;
  private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

  /** Best-effort push to every device registered for the user. Runs off the request thread. */
  @Async("pushNotificationExecutor")
  public void send(OrderPushNotificationEvent event) {
    FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
    if (messaging == null) {
      return;
    }

    List<String> tokens = fcmTokenRepository.findTokensByUserId(event.userId());
    if (tokens.isEmpty()) {
      return;
    }
    log.info("[FCM] enviando push para userId {} em {} token(s)", event.userId(), tokens.size());

    Notification notification =
        Notification.builder().setTitle(event.title()).setBody(event.body()).build();
    Map<String, String> data = buildData(event);
    AndroidConfig androidConfig =
        AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build();

    Set<String> staleTokens = new HashSet<>();
    for (int i = 0; i < tokens.size(); i += BATCH_SIZE) {
      List<String> batch = tokens.subList(i, Math.min(i + BATCH_SIZE, tokens.size()));
      MulticastMessage message =
          MulticastMessage.builder()
              .addAllTokens(batch)
              .setNotification(notification)
              .putAllData(data)
              .setAndroidConfig(androidConfig)
              .build();
      try {
        BatchResponse response = messaging.sendEachForMulticast(message);
        collectStaleTokens(batch, response, staleTokens);
      } catch (FirebaseMessagingException e) {
        log.warn("[FCM] falha ao enviar push para userId {}: {}", event.userId(), e.getMessage());
      }
    }

    if (!staleTokens.isEmpty()) {
      fcmTokenRepository.deleteByTokenIn(staleTokens);
      log.info(
          "[FCM] {} token(s) invalido(s) removido(s) para userId {}",
          staleTokens.size(),
          event.userId());
    }
  }

  private void collectStaleTokens(
      List<String> batch, BatchResponse response, Set<String> staleTokens) {
    List<SendResponse> responses = response.getResponses();
    for (int i = 0; i < responses.size(); i++) {
      SendResponse sendResponse = responses.get(i);
      if (sendResponse.isSuccessful()) {
        continue;
      }
      FirebaseMessagingException exception = sendResponse.getException();
      if (exception != null) {
        log.warn(
            "[FCM] token rejeitado: messagingErrorCode={} errorCode={} msg={}",
            exception.getMessagingErrorCode(),
            exception.getErrorCode(),
            exception.getMessage());
      }
      if (exception != null && STALE_TOKEN_CODES.contains(exception.getMessagingErrorCode())) {
        staleTokens.add(batch.get(i));
      }
    }
  }

  private Map<String, String> buildData(OrderPushNotificationEvent event) {
    Map<String, String> data = new HashMap<>();
    data.put("type", event.type().name());
    data.put("referenceType", event.referenceType().name());
    if (event.referenceId() != null) {
      String key =
          event.referenceType() == br.com.ragro.domain.enums.NotificationReferenceType.PRODUCT
              ? "productId"
              : "orderId";
      data.put(key, event.referenceId().toString());
    }
    return data;
  }
}
