package br.com.ragro.controller.request;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Position ping sent by the producer app via STOMP ({@code SEND /app/routes/{routeId}/position}).
 * Validated/filtered in {@code TrackingService} (poor accuracy and impossible jumps are discarded
 * before persisting/rebroadcasting).
 */
@Getter
@Setter
public class TrackingPositionMessage {

  private BigDecimal latitude;
  private BigDecimal longitude;

  /** GPS-reported accuracy, in meters (pings > maxAccuracyMeters are discarded). */
  private Double accuracyMeters;

  /** Reported speed, in km/h (optional; improves the ETA). */
  private Double speedKmh;
}
