package br.com.ragro.service.impl;

import br.com.ragro.service.api.PositionStore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Última posição por rota em memória, com expiração por inatividade (rota abandonada sem fechar
 * não retém localização para sempre). Suficiente para 1 task ECS; ver {@link PositionStore}.
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
