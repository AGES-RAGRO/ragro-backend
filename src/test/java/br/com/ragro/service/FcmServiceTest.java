package br.com.ragro.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.domain.enums.NotificationType;
import br.com.ragro.event.OrderPushNotificationEvent;
import br.com.ragro.repository.FcmTokenRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FcmTestResponses;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class FcmServiceTest {

  @Mock private FcmTokenRepository fcmTokenRepository;
  @Mock private ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
  @Mock private FirebaseMessaging firebaseMessaging;

  private FcmService fcmService() {
    return new FcmService(fcmTokenRepository, firebaseMessagingProvider);
  }

  private OrderPushNotificationEvent event(UUID userId) {
    return new OrderPushNotificationEvent(
        userId,
        "Pedido aceito",
        "Seu pedido foi aceito.",
        UUID.randomUUID(),
        NotificationType.ORDER_CONFIRMED);
  }

  private BatchResponse batchResponse(SendResponse... responses) {
    BatchResponse batchResponse = mock(BatchResponse.class);
    when(batchResponse.getResponses()).thenReturn(List.of(responses));
    return batchResponse;
  }

  @Test
  void send_whenFirebaseDisabled_shouldNoOp() throws Exception {
    when(firebaseMessagingProvider.getIfAvailable()).thenReturn(null);

    fcmService().send(event(UUID.randomUUID()));

    verify(fcmTokenRepository, never()).findTokensByUserId(any());
    verify(firebaseMessaging, never()).sendEachForMulticast(any());
  }

  @Test
  void send_whenNoTokens_shouldNotCallFirebase() throws Exception {
    UUID userId = UUID.randomUUID();
    when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
    when(fcmTokenRepository.findTokensByUserId(userId)).thenReturn(List.of());

    fcmService().send(event(userId));

    verify(firebaseMessaging, never()).sendEachForMulticast(any());
    verify(fcmTokenRepository, never()).deleteByTokenIn(any());
  }

  @Test
  void send_whenAllSucceed_shouldSendAndNotPrune() throws Exception {
    UUID userId = UUID.randomUUID();
    BatchResponse response = batchResponse(FcmTestResponses.success(), FcmTestResponses.success());
    when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
    when(fcmTokenRepository.findTokensByUserId(userId)).thenReturn(List.of("tok-a", "tok-b"));
    when(firebaseMessaging.sendEachForMulticast(any())).thenReturn(response);

    fcmService().send(event(userId));

    verify(firebaseMessaging).sendEachForMulticast(any(MulticastMessage.class));
    verify(fcmTokenRepository, never()).deleteByTokenIn(any());
  }

  @Test
  void send_whenTokensPermanentlyInvalid_shouldPruneOnlyThoseTokens() throws Exception {
    UUID userId = UUID.randomUUID();
    BatchResponse response =
        batchResponse(
            FcmTestResponses.success(),
            FcmTestResponses.failure(MessagingErrorCode.UNREGISTERED),
            FcmTestResponses.failure(MessagingErrorCode.INVALID_ARGUMENT));
    when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
    when(fcmTokenRepository.findTokensByUserId(userId)).thenReturn(List.of("good", "dead", "bad"));
    when(firebaseMessaging.sendEachForMulticast(any())).thenReturn(response);

    fcmService().send(event(userId));

    verify(fcmTokenRepository)
        .deleteByTokenIn(
            argThat(
                tokens -> tokens.size() == 2 && tokens.contains("dead") && tokens.contains("bad")));
  }

  @Test
  void send_whenTransientFailure_shouldNotPruneToken() throws Exception {
    UUID userId = UUID.randomUUID();
    BatchResponse response =
        batchResponse(FcmTestResponses.failure(MessagingErrorCode.UNAVAILABLE));
    when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
    when(fcmTokenRepository.findTokensByUserId(userId)).thenReturn(List.of("tok"));
    when(firebaseMessaging.sendEachForMulticast(any())).thenReturn(response);

    fcmService().send(event(userId));

    verify(fcmTokenRepository, never()).deleteByTokenIn(any());
  }
}
