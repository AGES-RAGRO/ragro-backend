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
 * Núcleo do rastreamento em tempo real: valida/filtra pings do produtor, mantém a última posição
 * ({@link PositionStore}), persiste a trilha (retenção 7 dias) e calcula ETA dinâmico projetando a
 * posição na polyline persistida da rota — ZERO chamadas ao Google por ping. Coordenadas nunca são
 * logadas (dado pessoal de localização).
 */
@Service
@RequiredArgsConstructor
public class TrackingService {

  private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

  /** Vias urbanas percorrem ~30% mais que a linha reta — corrige o ETA por distância direta. */
  private static final double ROUTE_SINUOSITY_FACTOR = 1.3;

  private final TrackingProperties properties;
  private final DeliveryRouteRepository deliveryRouteRepository;
  private final RouteStopRepository routeStopRepository;
  private final RoutePositionRepository routePositionRepository;
  private final PositionStore positionStore;

  /** Geometria decodificada por rota ativa (polyline → vértices + distâncias acumuladas). */
  private final Cache<UUID, RouteGeometry> geometryCache =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(6)).maximumSize(1_000).build();

  /** Último aceite por rota (rate limit de ingestão por intervalo mínimo). */
  private final Cache<UUID, Long> lastAcceptedAt =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(10_000).build();

  /**
   * Processa um ping do produtor. Retorna o broadcast a publicar, ou {@link Optional#empty()}
   * quando o ping foi descartado (ruído, rate limit, rota inativa).
   *
   * @param farmerUserId id do usuário autenticado no canal (validado como dono da rota aqui)
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

    // Rate limit por rota: pings mais frequentes que o intervalo mínimo são ignorados.
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
      // Emissor não é o produtor da rota — descarta silenciosamente e audita a tentativa.
      log.warn("Tracking ping rejected: user is not the route owner (route {})", routeId);
      return Optional.empty();
    }

    double lat = message.getLatitude().doubleValue();
    double lng = message.getLongitude().doubleValue();

    // Filtro de ruído: precisão ruim não entra na trilha nem é retransmitida.
    if (message.getAccuracyMeters() != null
        && message.getAccuracyMeters() > properties.getMaxAccuracyMeters()) {
      return Optional.empty();
    }

    // Filtro de salto impossível: velocidade implícita vs. última posição aceita.
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

    lastAcceptedAt.put(routeId, now);
    LastPosition position =
        new LastPosition(
            message.getLatitude(), message.getLongitude(), recordedAt, message.getSpeedKmh());
    positionStore.put(routeId, position);

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

    return Optional.of(buildBroadcast(route, lat, lng, recordedAt));
  }

  /**
   * Snapshot de rastreamento de um pedido para o SEU cliente (estado inicial da tela + fallback
   * de polling). Autorização: somente o cliente do pedido.
   */
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
      // Sem ping ainda: usa o ETA estático calculado na criação da rota.
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

  /** Autorização do SUBSCRIBE: produtor da rota ou cliente de alguma entrega dela. */
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

  /** Rota terminou: para de compartilhar posição (privacidade fora de entrega ativa). */
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
   * ETA dinâmico (sem custo de API): distância direta produtor→parada corrigida por um fator de
   * sinuosidade urbano, dividida pela velocidade média da rota (distância/duração do Google na
   * criação). NÃO projeta na polyline por vértice: a rota é round-trip e o vértice mais próximo do
   * produtor podia cair na perna de VOLTA, zerando a distância restante até uma parada da ida (bug
   * que dava ETA ~0). A distância direta é robusta e honesta o bastante para um ETA de entrega.
   */
  private Integer etaSecondsToStop(DeliveryRoute route, RouteStop stop, double lat, double lng) {
    double directMeters =
        PolylineUtil.distanceMeters(
            lat, lng, stop.getLatitude().doubleValue(), stop.getLongitude().doubleValue());
    double estimatedRoadMeters = directMeters * ROUTE_SINUOSITY_FACTOR;
    return (int) Math.round(estimatedRoadMeters / averageSpeedMs(route));
  }

  /**
   * Polyline que o cliente vê: recortada ao trecho da posição atual do produtor (ou da origem da
   * rota, se ainda não houve ping) até a parada DELE. Não expõe as paradas seguintes nem a volta à
   * origem (privacidade entre clientes da mesma rota).
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
    return 30 / 3.6; // fallback: 30 km/h urbano
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

  /** Polyline decodificada — usada para detectar desvio (distância do produtor ao traçado). */
  static final class RouteGeometry {
    final List<Point> vertices;

    private RouteGeometry(List<Point> vertices) {
      this.vertices = vertices;
    }

    static RouteGeometry of(String encodedPolyline) {
      return new RouteGeometry(PolylineUtil.decode(encodedPolyline));
    }

    /** Menor distância do ponto a qualquer vértice da polyline (aproxima a distância ao traçado). */
    double distanceToNearestVertexMeters(double lat, double lng) {
      double best = Double.MAX_VALUE;
      for (Point p : vertices) {
        best = Math.min(best, PolylineUtil.distanceMeters(lat, lng, p.lat(), p.lng()));
      }
      return best;
    }
  }
}
