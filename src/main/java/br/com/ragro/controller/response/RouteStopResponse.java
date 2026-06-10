package br.com.ragro.controller.response;

import br.com.ragro.domain.enums.RouteStopStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RouteStopResponse {

  private UUID id;
  private UUID orderId;
  /** Posição na rota otimizada (0-based, ordem de visita). */
  private int sequence;
  private RouteStopStatus status;
  private BigDecimal latitude;
  private BigDecimal longitude;
  private String addressText;
  private String customerName;
  /** Distância/duração da leg que chega nesta parada. */
  private BigDecimal legDistanceKm;
  private Integer legDurationSeconds;
  /** ETA absoluto estimado na criação da rota. */
  private OffsetDateTime eta;
  private OffsetDateTime completedAt;
}
