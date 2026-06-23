package br.com.ragro.service;

import br.com.ragro.config.TrackingProperties;
import br.com.ragro.controller.request.TrackingPositionMessage;
import br.com.ragro.controller.response.OrderTrackingResponse;
import br.com.ragro.controller.response.TrackingBroadcast;
import br.com.ragro.domain.DeliveryRoute;
import br.com.ragro.domain.RoutePosition;
import br.com.ragro.domain.RouteStop;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.DeliveryRouteStatus;
import br.com.ragro.domain.enums.RouteStopStatus;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.repository.DeliveryRouteRepository;
import br.com.ragro.repository.RoutePositionRepository;
import br.com.ragro.repository.RouteStopRepository;
import br.com.ragro.service.PolylineUtil.Point;
import br.com.ragro.service.api.PositionStore;
import br.com.ragro.service.api.PositionStore.LastPosition;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-time tracking core: validates/filters producer pings, keeps the last position ({@link
 * PositionStore}), persists the trail (7-day retention), and computes a dynamic ETA — zero Google calls
 * per ping. Coordinates are never logged (personal location data).
 */
@Service
@RequiredArgsConstructor
public class TrackingService {

  private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

  /** Urban roads run ~30% longer than a straight line — corrects the direct-distance ETA. */
  private static final double ROUTE_SINUOSITY_FACTOR = 1.3;

  private final TrackingProperties properties;
  private final DeliveryRouteRepository deliveryRouteRepository;
  private final RouteStopRepository routeStopRepository;
  private final RoutePositionRepository routePositionRepository;
  private final PositionStore positionStore;

  /** Decoded geometry per active route (polyline → vertices + cumulative distances). */
  private final Cache<UUID, RouteGeometry> geometryCache =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(6)).maximumSize(1_000).build();

  /** Last accept time per route (min-interval ingestion rate limit). */
  private final Cache<UUID, Long> lastAcceptedAt =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(10_000).build();

  /**
   * Processes a producer ping. Returns the broadcast to publish, or {@link Optional#empty()} when the ping
   * was dropped (noise, rate limit, inactive route).
   *
   * @param farmerUserId authenticated channel user (validated here as the route owner)
   */
  @Transactional
  public Optional<TrackingBroadcast> ingestPosition(
      UUID routeId, UUID farmerUserId, TrackingPositionMessage message) {
    if (!properties.isEnabled()
        || message == null
        || message.getLatitude() == null
        || message.getLongitude() == null) {
      return Optional.empty();
    }

    // Per-route rate limit: pings faster than the min interval are ignored.
    long now = System.currentTimeMillis();
    Long last = lastAcceptedAt.getIfPresent(routeId);
    if (last != null && now - last < properties.getMinIntervalMs()) {
      return Optional.empty();
    }

    DeliveryRoute route = deliveryRouteRepository.findWithStopsById(routeId).orElse(null);
    if (route == null || route.getStatus() != DeliveryRouteStatus.ACTIVE) {
      return Optional.empty();
    }
    if (!route.getFarmer().getId().equals(farmerUserId)) {
      // Sender is not the route's producer — drop silently and audit the attempt.
      log.warn("Tracking ping rejected: user is not the route owner (route {})", routeId);
      return Optional.empty();
    }

    double lat = message.getLatitude().doubleValue();
    double lng = message.getLongitude().doubleValue();

    // Noise filter: poor accuracy is not stored in the trail nor rebroadcast.
    if (message.getAccuracyMeters() != null
        && message.getAccuracyMeters() > properties.getMaxAccuracyMeters()) {
      return Optional.empty();
    }

    // Impossible-jump filter: implied speed vs. the last accepted position.
    LastPosition previous = positionStore.get(routeId);
    OffsetDateTime recordedAt = OffsetDateTime.now();
    if (previous != null) {
      double meters =
          PolylineUtil.distanceMeters(
              previous.latitude().doubleValue(), previous.longitude().doubleValue(), lat, lng);
      double seconds =
          Math.max(1, Duration.between(previous.recordedAt(), recordedAt).toMillis() / 1000.0);
      double impliedKmh = (meters / seconds) * 3.6;
      if (impliedKmh > properties.getMaxSpeedKmh()) {
        return Optional.empty();
      }
    }

    RoutePosition trailPoint = new RoutePosition();
    trailPoint.setRoute(route);
    trailPoint.setLatitude(message.getLatitude());
    trailPoint.setLongitude(message.getLongitude());
    trailPoint.setAccuracyMeters(
        message.getAccuracyMeters() != null
            ? BigDecimal.valueOf(message.getAccuracyMeters())
            : null);
    trailPoint.setSpeedKmh(
        message.getSpeedKmh() != null ? BigDecimal.valueOf(message.getSpeedKmh()) : null);
    trailPoint.setRecordedAt(recordedAt);
    routePositionRepository.save(trailPoint);

    // Update in-memory state (rate limit + last position) only after persisting, so a failed save leaves
    // no ghost position and does not consume the rate limit.
    lastAcceptedAt.put(routeId, now);
    LastPosition position =
        new LastPosition(
            message.getLatitude(), message.getLongitude(), recordedAt, message.getSpeedKmh());
    positionStore.put(routeId, position);

    return Optional.of(buildBroadcast(route, lat, lng, recordedAt));
  }

  /** Tracking snapshot of an order for its customer (initial screen state + polling fallback). Customer-only. */
  @Transactional(readOnly = true)
  public OrderTrackingResponse trackingForOrder(UUID orderId, User user) {
    if (!properties.isEnabled()) {
      return OrderTrackingResponse.builder().available(false).build();
    }
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas o cliente do pedido acompanha a entrega");
    }
    RouteStop stop =
        routeStopRepository
            .findByOrderIdAndRouteStatus(orderId, DeliveryRouteStatus.ACTIVE)
            .orElse(null);
    if (stop == null) {
      return OrderTrackingResponse.builder().available(false).build();
    }
    if (!stop.getOrder().getCustomer().getId().equals(user.getId())) {
      throw new ForbiddenException("Você não tem permissão para acompanhar este pedido");
    }

    DeliveryRoute route = stop.getRoute();
    LastPosition position = positionStore.get(route.getId());
    Integer etaSeconds = null;
    if (position != null) {
      etaSeconds =
          etaSecondsToStop(
              route, stop, position.latitude().doubleValue(), position.longitude().doubleValue());
    } else if (stop.getEta() != null) {
      // No ping yet: use the static ETA computed at route creation.
      long staticEta = Duration.between(OffsetDateTime.now(), stop.getEta()).toSeconds();
      etaSeconds = (int) Math.max(0, staticEta);
    }

    return OrderTrackingResponse.builder()
        .available(true)
        .routeId(route.getId())
        .producerLatitude(position != null ? position.latitude() : null)
        .producerLongitude(position != null ? position.longitude() : null)
        .recordedAt(position != null ? position.recordedAt() : null)
        .destinationLatitude(stop.getLatitude())
        .destinationLongitude(stop.getLongitude())
        .etaSeconds(etaSeconds)
        .stopsBefore(pendingStopsBefore(route, stop))
        .stopStatus(stop.getStatus().name())
        .overviewPolyline(polylineForStop(route, stop, position))
        .build();
  }

  /** SUBSCRIBE authorization: the route's producer or a customer of one of its deliveries. */
  @Transactional(readOnly = true)
  public boolean canSubscribe(UUID routeId, UUID userId, TypeUser type) {
    if (type == TypeUser.FARMER) {
      return deliveryRouteRepository
          .findById(routeId)
          .map(route -> route.getFarmer().getId().equals(userId))
          .orElse(false);
    }
    if (type == TypeUser.CUSTOMER) {
      return routeStopRepository.existsByRouteIdAndOrderCustomerId(routeId, userId);
    }
    return false;
  }

  /** Route ended: stop sharing position (privacy outside an active delivery). */
  public void clearRoute(UUID routeId) {
    positionStore.clear(routeId);
    geometryCache.invalidate(routeId);
  }

  private TrackingBroadcast buildBroadcast(
      DeliveryRoute route, double lat, double lng, OffsetDateTime recordedAt) {
    RouteGeometry geometry = geometryOf(route);
    boolean deviated = false;
    if (geometry != null) {
      double off = geometry.distanceToNearestVertexMeters(lat, lng);
      deviated = off > properties.getDeviationMeters();
    }

    List<RouteStop> ordered =
        route.getStops().stream().sorted(Comparator.comparingInt(RouteStop::getSequence)).toList();

    List<TrackingBroadcast.StopEta> etas = new ArrayList<>(ordered.size());
    int pendingBefore = 0;
    for (RouteStop stop : ordered) {
      boolean terminal =
          stop.getStatus() == RouteStopStatus.DELIVERED
              || stop.getStatus() == RouteStopStatus.FAILED;
      Integer eta = terminal ? null : etaSecondsToStop(route, stop, lat, lng);
      etas.add(
          TrackingBroadcast.StopEta.builder()
              .orderId(stop.getOrder().getId())
              .sequence(stop.getSequence())
              .etaSeconds(eta)
              .stopsBefore(terminal ? 0 : pendingBefore)
              .status(stop.getStatus().name())
              .build());
      if (!terminal) {
        pendingBefore++;
      }
    }

    return TrackingBroadcast.builder()
        .latitude(BigDecimal.valueOf(lat))
        .longitude(BigDecimal.valueOf(lng))
        .recordedAt(recordedAt)
        .deviated(deviated)
        .etas(etas)
        .build();
  }

  /**
   * Dynamic ETA (no API cost): direct producer→stop distance times the urban sinuosity factor, divided by
   * the route's average speed. Does NOT project onto the polyline by vertex: on a round-trip route the
   * nearest vertex could land on the return leg, zeroing the distance to an outbound stop (ETA ~0 bug).
   */
  private Integer etaSecondsToStop(DeliveryRoute route, RouteStop stop, double lat, double lng) {
    double directMeters =
        PolylineUtil.distanceMeters(
            lat, lng, stop.getLatitude().doubleValue(), stop.getLongitude().doubleValue());
    double estimatedRoadMeters = directMeters * ROUTE_SINUOSITY_FACTOR;
    return (int) Math.round(estimatedRoadMeters / averageSpeedMs(route));
  }

  /**
   * Polyline the customer sees: clipped from the producer's current position (or route origin if no ping
   * yet) to their stop. Hides later stops and the return leg (privacy among customers of the same route).
   */
  private String polylineForStop(DeliveryRoute route, RouteStop stop, LastPosition position) {
    String full = route.getOverviewPolyline();
    if (full == null || full.isBlank()) {
      return null;
    }
    double fromLat =
        position != null ? position.latitude().doubleValue() : route.getOriginLatitude().doubleValue();
    double fromLng =
        position != null
            ? position.longitude().doubleValue()
            : route.getOriginLongitude().doubleValue();
    return PolylineUtil.clip(
        full, fromLat, fromLng, stop.getLatitude().doubleValue(), stop.getLongitude().doubleValue());
  }

  private double averageSpeedMs(DeliveryRoute route) {
    if (route.getTotalDistanceKm() != null
        && route.getTotalDurationSeconds() != null
        && route.getTotalDurationSeconds() > 0) {
      double ms =
          route.getTotalDistanceKm().doubleValue() * 1000.0 / route.getTotalDurationSeconds();
      if (ms > 1) {
        return ms;
      }
    }
    return 30 / 3.6; // fallback: 30 km/h urban
  }

  private RouteGeometry geometryOf(DeliveryRoute route) {
    if (route.getOverviewPolyline() == null || route.getOverviewPolyline().isBlank()) {
      return null;
    }
    return geometryCache.get(route.getId(), id -> RouteGeometry.of(route.getOverviewPolyline()));
  }

  private int pendingStopsBefore(DeliveryRoute route, RouteStop target) {
    return (int)
        route.getStops().stream()
            .filter(s -> s.getSequence() < target.getSequence())
            .filter(
                s ->
                    s.getStatus() != RouteStopStatus.DELIVERED
                        && s.getStatus() != RouteStopStatus.FAILED)
            .count();
  }

  /** Decoded polyline — used to detect deviation (producer's distance to the route). */
  static final class RouteGeometry {
    final List<Point> vertices;

    private RouteGeometry(List<Point> vertices) {
      this.vertices = vertices;
    }

    static RouteGeometry of(String encodedPolyline) {
      return new RouteGeometry(PolylineUtil.decode(encodedPolyline));
    }

    /** Smallest distance from the point to any polyline vertex (approximates distance to the route). */
    double distanceToNearestVertexMeters(double lat, double lng) {
      double best = Double.MAX_VALUE;
      for (Point p : vertices) {
        best = Math.min(best, PolylineUtil.distanceMeters(lat, lng, p.lat(), p.lng()));
      }
      return best;
    }
  }
}
