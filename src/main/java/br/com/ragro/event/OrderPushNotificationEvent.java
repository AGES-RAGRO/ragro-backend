package br.com.ragro.event;

import br.com.ragro.domain.enums.NotificationType;
import java.util.UUID;

/**
 * Published when an order notification is persisted so the push can be delivered after the
 * transaction commits, off the request thread. Carries already-resolved values (no JPA entities) to
 * avoid lazy-loading once the originating transaction is closed.
 */
public record OrderPushNotificationEvent(
    UUID userId, String title, String body, UUID orderId, NotificationType type) {}
