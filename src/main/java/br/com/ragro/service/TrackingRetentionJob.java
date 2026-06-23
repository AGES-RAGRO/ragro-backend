package br.com.ragro.service;

import br.com.ragro.config.TrackingProperties;
import br.com.ragro.repository.RoutePositionRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily purge of the GPS trail beyond the 7-day retention window (LGPD; kept for support/disputes).
 * Logs only the count, never coordinates.
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ragro.tracking.enabled", havingValue = "true")
public class TrackingRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(TrackingRetentionJob.class);

  private final TrackingProperties properties;
  private final RoutePositionRepository routePositionRepository;

  @Scheduled(cron = "0 0 4 * * *")
  @Transactional
  public void purgeExpiredPositions() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusDays(properties.getRetentionDays());
    int removed = routePositionRepository.deleteOlderThan(cutoff);
    if (removed > 0) {
      log.info("Tracking retention: purged {} GPS points older than {} days",
          removed, properties.getRetentionDays());
    }
  }
}
