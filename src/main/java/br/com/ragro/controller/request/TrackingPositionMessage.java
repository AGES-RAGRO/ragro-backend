package br.com.ragro.controller.request;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Ping de posição enviado pelo app do produtor via STOMP ({@code SEND
 * /app/routes/{routeId}/position}). Validado/filtrado no {@code TrackingService} (precisão ruim e
 * saltos impossíveis são descartados antes de persistir/retransmitir).
 */
@Getter
@Setter
public class TrackingPositionMessage {

  private BigDecimal latitude;
  private BigDecimal longitude;

  /** Precisão reportada pelo GPS, em metros (pings > maxAccuracyMeters são descartados). */
  private Double accuracyMeters;

  /** Velocidade reportada, em km/h (opcional; melhora o ETA). */
  private Double speedKmh;
}
