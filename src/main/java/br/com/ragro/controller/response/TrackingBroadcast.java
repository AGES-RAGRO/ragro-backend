package br.com.ragro.controller.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Payload publicado em {@code /topic/routes/{routeId}} a cada ping aceito. Vai para TODOS os
 * assinantes autorizados da rota (produtor + clientes das entregas), então carrega apenas dados
 * seguros: posição do produtor e ETA por pedido — sem endereços/nomes de outros clientes (cada
 * app filtra a entrada do próprio orderId).
 */
@Getter
@Builder
public class TrackingBroadcast {

  private BigDecimal latitude;
  private BigDecimal longitude;
  private OffsetDateTime recordedAt;

  /** Produtor está fora da rota planejada (>200m da polyline) — UI pode sinalizar. */
  private boolean deviated;

  private List<StopEta> etas;

  @Getter
  @Builder
  public static class StopEta {
    private UUID orderId;
    /** Posição de visita da parada (0-based); ETA em segundos a partir de agora. */
    private int sequence;
    private Integer etaSeconds;
    /** Quantas paradas pendentes existem antes desta. */
    private int stopsBefore;
    private String status;
  }
}
