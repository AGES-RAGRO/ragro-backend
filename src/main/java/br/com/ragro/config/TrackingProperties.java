package br.com.ragro.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Real-time tracking configuration (Phase 3). */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ragro.tracking")
public class TrackingProperties {

  /** Toggles the entire WebSocket/STOMP channel on/off (instant rollback). */
  private boolean enabled = true;

  /** GPS trail retention window (product decision: 7 days, LGPD-based). */
  @Min(1)
  private int retentionDays = 7;

  /** Pings with accuracy worse than this are dropped (GPS noise). */
  @Min(1)
  private double maxAccuracyMeters = 50;

  /** A jump implying speed above this is dropped (GPS teleport). */
  @Min(1)
  private double maxSpeedKmh = 150;

  /** Minimum interval between accepted pings per route (ingestion rate limit). */
  @Min(100)
  private long minIntervalMs = 2000;

  /** Distance from the polyline beyond which the producer is considered off-route. */
  @Min(10)
  private double deviationMeters = 200;
}
