package br.com.ragro.listener;

import br.com.ragro.event.OrderPushNotificationEvent;
import br.com.ragro.service.FcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges domain events to the push sender. Fires only after the originating transaction commits
 * (so the notification is durable and no DB locks are held), then delegates to the {@code @Async}
 * {@link FcmService#send} on a separate bean — keeping the async proxy and the transactional-event
 * proxy on distinct methods, which is the reliable composition.
 */
@Component
@RequiredArgsConstructor
public class PushNotificationListener {

  private final FcmService fcmService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOrderPush(OrderPushNotificationEvent event) {
    fcmService.send(event);
  }
}
