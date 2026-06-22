package br.com.ragro.service.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Última posição conhecida por rota ativa — serve novos assinantes/polling sem esperar o próximo
 * ping. Implementação atual em memória (1 task ECS); com múltiplas instâncias, trocar por Redis
 * implementando esta interface.
 */
public interface PositionStore {

  record LastPosition(
      BigDecimal latitude, BigDecimal longitude, OffsetDateTime recordedAt, Double speedKmh) {}

  void put(UUID routeId, LastPosition position);

  LastPosition get(UUID routeId);

  /** Rota terminou: posição não é mais compartilhada (privacidade fora de entrega ativa). */
  void clear(UUID routeId);
}
