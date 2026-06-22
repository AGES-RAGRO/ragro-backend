package br.com.ragro.controller.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Snapshot de rastreamento de UM pedido para o SEU cliente ({@code GET /orders/{id}/tracking}).
 * Usado como estado inicial da tela e como fallback de polling quando o WebSocket cai. Payload
 * mínimo por privacidade: nada sobre as demais paradas além da contagem à frente.
 */
@Getter
@Builder
public class OrderTrackingResponse {

  /** false quando o pedido não está em rota ativa (sem posição para compartilhar). */
  private boolean available;

  /** Id da rota ativa — canal STOMP: /topic/routes/{routeId}. */
  private UUID routeId;

  private BigDecimal producerLatitude;
  private BigDecimal producerLongitude;
  private OffsetDateTime recordedAt;

  /** Destino desta entrega (do snapshot do pedido). */
  private BigDecimal destinationLatitude;
  private BigDecimal destinationLongitude;

  private Integer etaSeconds;
  private int stopsBefore;

  /** Status da parada desta entrega: PENDING | ARRIVED | DELIVERED | FAILED. */
  private String stopStatus;

  /**
   * Polyline codificada (Google) do caminho até ESTA entrega — recortada no servidor ao trecho
   * [posição atual do produtor (ou origem) → parada do cliente], para desenhar no mapa. Não inclui
   * as paradas seguintes nem a volta à origem (privacidade entre clientes da rota). Vem só neste
   * snapshot (não nos broadcasts de posição).
   */
  private String overviewPolyline;
}
