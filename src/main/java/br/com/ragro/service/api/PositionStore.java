package br.com.ragro.service.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Last known position per active route — serves new subscribers/polling without waiting for the next
 * ping. In-memory impl assumes 1 ECS task; for multiple instances, implement this with Redis.
 */
public interface PositionStore {

  record LastPosition(
      BigDecimal latitude, BigDecimal longitude, OffsetDateTime recordedAt, Double speedKmh) {}

  void put(UUID routeId, LastPosition position);

  LastPosition get(UUID routeId);

  /** Route finished: position no longer shared (privacy outside an active delivery). */
  void clear(UUID routeId);
}
