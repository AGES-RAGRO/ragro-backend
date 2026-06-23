package br.com.ragro.listener;

import static org.mockito.Mockito.verify;

import br.com.ragro.domain.enums.NotificationReferenceType;
import br.com.ragro.domain.enums.NotificationType;
import br.com.ragro.event.OrderPushNotificationEvent;
import br.com.ragro.service.FcmService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationListenerTest {

  @Mock private FcmService fcmService;
  @InjectMocks private PushNotificationListener listener;

  @Test
  void onOrderPush_shouldDelegateToFcmService() {
    OrderPushNotificationEvent event =
        new OrderPushNotificationEvent(
            UUID.randomUUID(),
            "Pedido aceito",
            "Seu pedido foi aceito.",
            UUID.randomUUID(),
            NotificationType.ORDER_CONFIRMED,
            NotificationReferenceType.ORDER);

    listener.onOrderPush(event);

    verify(fcmService).send(event);
  }
}
