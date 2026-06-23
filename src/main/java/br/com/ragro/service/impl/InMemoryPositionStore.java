package br.com.ragro.service.impl;

import br.com.ragro.service.api.PositionStore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Last position per route, in memory, expiring on inactivity (an abandoned route doesn't keep its
 * location forever). Sufficient for 1 ECS task; see {@link PositionStore}.
 */
@Component
public class InMemoryPositionStore implements PositionStore {

  private final Cache<UUID, LastPosition> positions =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6)).maximumSize(10_000).build();

  @Override
  public void put(UUID routeId, LastPosition position) {
    positions.put(routeId, position);
  }

  @Override
  public LastPosition get(UUID routeId) {
    return positions.getIfPresent(routeId);
  }

  @Override
  public void clear(UUID routeId) {
    positions.invalidate(routeId);
  }
}
