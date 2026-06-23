package br.com.ragro.service;

import br.com.ragro.controller.request.CreateRouteRequest;
import br.com.ragro.controller.response.RouteResponse;
import br.com.ragro.domain.AddressSnapshot;
import br.com.ragro.domain.DeliveryRoute;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.RouteStop;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.DeliveryRouteStatus;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.RouteStopStatus;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.DeliveryRouteMapper;
import br.com.ragro.repository.DeliveryRouteRepository;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.RouteStopRepository;
import br.com.ragro.service.GoogleMapsService.GeocodeOutcome;
import br.com.ragro.service.GoogleRoutesService.ComputedRoute;
import br.com.ragro.service.GoogleRoutesService.GeoPoint;
import br.com.ragro.service.GoogleRoutesService.RouteLeg;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persisted delivery route: computed ONCE at Google (optimized round trip, with traffic), persisted
 * with ordered stops and per-leg ETA. Delivery confirmations are a PATCH per stop with no further
 * Google calls.
 */
@Service
@RequiredArgsConstructor
public class DeliveryRouteService {

  private static final Set<RouteStopStatus> TERMINAL_STOP_STATUSES =
      Set.of(RouteStopStatus.DELIVERED, RouteStopStatus.FAILED);

  private final UserService userService;
  private final OrderRepository orderRepository;
  private final DeliveryRouteRepository deliveryRouteRepository;
  private final RouteStopRepository routeStopRepository;
  private final GoogleRoutesService googleRoutesService;
  private final GoogleMapsService googleMapsService;
  private final OrderService orderService;
  private final TrackingService trackingService;

  /**
   * Creates the producer's active route from CONFIRMED/IN_DELIVERY orders. Any previous active route
   * is closed first ({@link #retirePreviousRoute}). CONFIRMED orders transition to IN_DELIVERY via
   * the state machine (with a customer notification via event).
   */
  @Transactional
  public RouteResponse createRoute(CreateRouteRequest request, Jwt jwt) {
    User user = requireFarmer(jwt);

    deliveryRouteRepository
        .findByFarmerIdAndStatus(user.getId(), DeliveryRouteStatus.ACTIVE)
        .ifPresent(this::retirePreviousRoute);

    List<Order> orders =
        orderRepository.findByFarmerIdAndStatusInOrderByCreatedAtAsc(
            user.getId(), List.of(OrderStatus.CONFIRMED, OrderStatus.IN_DELIVERY));
    if (orders.isEmpty()) {
      throw new BusinessException("Nenhum pedido confirmado ou em entrega para montar a rota");
    }

    List<GeoPoint> points = new ArrayList<>(orders.size());
    List<String> addressTexts = new ArrayList<>(orders.size());
    for (Order order : orders) {
      AddressSnapshot snapshot = order.getDeliveryAddressSnapshot();
      String addressText = snapshotText(snapshot);
      GeoPoint point = resolveCoordinates(order, snapshot, addressText);
      points.add(point);
      addressTexts.add(addressText);
    }

    GeoPoint origin = new GeoPoint(request.getOriginLatitude(), request.getOriginLongitude());
    ComputedRoute computed = googleRoutesService.computeOptimizedRoundTrip(origin, points);

    DeliveryRoute route = new DeliveryRoute();
    route.setFarmer(orders.get(0).getFarmer());
    route.setStatus(DeliveryRouteStatus.ACTIVE);
    route.setOriginLatitude(origin.latitude());
    route.setOriginLongitude(origin.longitude());
    route.setTotalDistanceKm(computed.totalDistanceKm());
    route.setTotalDurationSeconds(computed.totalDurationSeconds());
    route.setOverviewPolyline(computed.encodedPolyline());
    route.setBaselineDistanceKm(computeBaseline(origin, points));

    OffsetDateTime etaCursor = OffsetDateTime.now();
    List<Integer> visitOrder = computed.optimizedOrder();
    for (int visit = 0; visit < visitOrder.size(); visit++) {
      int inputIndex = visitOrder.get(visit);
      // Defensive: an out-of-range index would throw IndexOutOfBounds (500). Fail early and clearly.
      if (inputIndex < 0 || inputIndex >= orders.size()) {
        throw new BusinessException(
            "Rota calculada com parada inválida (índice fora do intervalo)");
      }
      Order order = orders.get(inputIndex);
      RouteLeg leg = visit < computed.legs().size() ? computed.legs().get(visit) : null;
      if (leg != null) {
        etaCursor = etaCursor.plusSeconds(leg.durationSeconds());
      }

      RouteStop stop = new RouteStop();
      stop.setRoute(route);
      stop.setOrder(order);
      stop.setSequence(visit);
      stop.setStatus(RouteStopStatus.PENDING);
      stop.setLatitude(points.get(inputIndex).latitude());
      stop.setLongitude(points.get(inputIndex).longitude());
      stop.setAddressText(addressTexts.get(inputIndex));
      if (leg != null) {
        stop.setLegDistanceKm(leg.distanceKm());
        stop.setLegDurationSeconds(leg.durationSeconds());
        stop.setEta(etaCursor);
      }
      route.getStops().add(stop);
    }

    DeliveryRoute saved = deliveryRouteRepository.saveAndFlush(route);

    // Out for delivery: CONFIRMED orders transition to IN_DELIVERY (side effects + notification
    // via OrderService's state machine).
    for (Order order : orders) {
      if (order.getStatus() == OrderStatus.CONFIRMED) {
        orderService.updateOrderStatus(order.getId(), OrderStatus.IN_DELIVERY, jwt);
      }
    }

    return DeliveryRouteMapper.toResponse(saved);
  }

  /**
   * Adds newly CONFIRMED orders to the producer's ACTIVE route WITHOUT losing progress: DELIVERED/FAILED
   * stops are kept as history (lower sequence preserved), and only the pending stops + the new orders are
   * re-optimized (single Google call). The new orders transition CONFIRMED → IN_DELIVERY atomically.
   *
   * <p>Idempotent and churn-free: with no new eligible order it only self-heals and returns the route
   * unchanged (no Google call, no re-sequencing), so the mobile pull-to-refresh can call it safely. The
   * CO2 baseline grows by the new orders' round-trip delta only — no double counting of existing stops.
   */
  @Transactional
  public RouteResponse addStops(UUID routeId, Jwt jwt) {
    User user = requireFarmer(jwt);

    DeliveryRoute route =
        deliveryRouteRepository
            .findWithStopsById(routeId)
            .orElseThrow(() -> new NotFoundException("Rota não encontrada"));
    if (!route.getFarmer().getId().equals(user.getId())) {
      throw new ForbiddenException("Você não tem permissão para acessar esta rota");
    }
    if (route.getStatus() != DeliveryRouteStatus.ACTIVE) {
      throw new BusinessException("A rota não está mais ativa");
    }

    // Self-heal stops whose order finished outside the stop flow; close the route if all terminal.
    boolean changed = reconcileStopsWithOrders(route);
    if (completeIfAllStopsTerminal(route)) {
      deliveryRouteRepository.saveAndFlush(route);
      throw new NotFoundException("Nenhuma rota ativa");
    }

    Set<UUID> ordersOnRoute =
        route.getStops().stream().map(s -> s.getOrder().getId()).collect(Collectors.toSet());
    List<Order> newOrders =
        orderRepository
            .findByFarmerIdAndStatusInOrderByCreatedAtAsc(user.getId(), List.of(OrderStatus.CONFIRMED))
            .stream()
            .filter(o -> !ordersOnRoute.contains(o.getId()))
            .toList();

    if (newOrders.isEmpty()) {
      // Nothing new: skip the Google call and DON'T re-sequence pending stops (no churn).
      if (changed) {
        deliveryRouteRepository.saveAndFlush(route);
      }
      return DeliveryRouteMapper.toResponse(route);
    }

    // Carries each re-optimized point's source: an existing pending stop OR a brand-new order.
    record StopInput(GeoPoint point, String addressText, RouteStop existing, Order newOrder) {}

    List<RouteStop> pendingStops =
        route.getStops().stream()
            .filter(s -> !TERMINAL_STOP_STATUSES.contains(s.getStatus()))
            .toList();

    List<StopInput> inputs = new ArrayList<>(pendingStops.size() + newOrders.size());
    for (RouteStop stop : pendingStops) {
      inputs.add(
          new StopInput(
              new GeoPoint(stop.getLatitude(), stop.getLongitude()),
              stop.getAddressText(),
              stop,
              null));
    }
    List<GeoPoint> newPoints = new ArrayList<>(newOrders.size());
    for (Order order : newOrders) {
      AddressSnapshot snapshot = order.getDeliveryAddressSnapshot();
      String addressText = snapshotText(snapshot);
      GeoPoint point = resolveCoordinates(order, snapshot, addressText);
      inputs.add(new StopInput(point, addressText, null, order));
      newPoints.add(point);
    }

    GeoPoint origin = new GeoPoint(route.getOriginLatitude(), route.getOriginLongitude());
    ComputedRoute computed =
        googleRoutesService.computeOptimizedRoundTrip(
            origin, inputs.stream().map(StopInput::point).toList());

    // Re-sequence pending + new stops into a range ABOVE every existing sequence
    // (terminal AND the pending stops being reordered). Disjoint ranges avoid a
    // transient duplicate against uq_route_stops_route_sequence while Hibernate
    // interleaves the INSERTs (new stops) and UPDATEs (reordered pending stops).
    int base =
        1
            + route.getStops().stream()
                .mapToInt(RouteStop::getSequence)
                .max()
                .orElse(-1);

    OffsetDateTime etaCursor = OffsetDateTime.now();
    List<Integer> visitOrder = computed.optimizedOrder();
    for (int visit = 0; visit < visitOrder.size(); visit++) {
      int inputIndex = visitOrder.get(visit);
      if (inputIndex < 0 || inputIndex >= inputs.size()) {
        throw new BusinessException(
            "Rota calculada com parada inválida (índice fora do intervalo)");
      }
      StopInput in = inputs.get(inputIndex);
      RouteLeg leg = visit < computed.legs().size() ? computed.legs().get(visit) : null;
      if (leg != null) {
        etaCursor = etaCursor.plusSeconds(leg.durationSeconds());
      }

      RouteStop stop = in.existing();
      if (stop == null) {
        stop = new RouteStop();
        stop.setRoute(route);
        stop.setOrder(in.newOrder());
        stop.setStatus(RouteStopStatus.PENDING);
        stop.setLatitude(in.point().latitude());
        stop.setLongitude(in.point().longitude());
        stop.setAddressText(in.addressText());
        route.getStops().add(stop);
      }
      stop.setSequence(base + visit);
      if (leg != null) {
        stop.setLegDistanceKm(leg.distanceKm());
        stop.setLegDurationSeconds(leg.durationSeconds());
        stop.setEta(etaCursor);
      }
    }

    route.setTotalDistanceKm(computed.totalDistanceKm());
    route.setTotalDurationSeconds(computed.totalDurationSeconds());
    route.setOverviewPolyline(computed.encodedPolyline());
    BigDecimal baseline =
        route.getBaselineDistanceKm() == null ? BigDecimal.ZERO : route.getBaselineDistanceKm();
    route.setBaselineDistanceKm(baseline.add(computeBaseline(origin, newPoints)));

    DeliveryRoute saved = deliveryRouteRepository.saveAndFlush(route);

    // Out for delivery: new CONFIRMED orders -> IN_DELIVERY (atomic with this tx; notification via event).
    for (Order order : newOrders) {
      if (order.getStatus() == OrderStatus.CONFIRMED) {
        orderService.updateOrderStatus(order.getId(), OrderStatus.IN_DELIVERY, jwt);
      }
    }

    return DeliveryRouteMapper.toResponse(saved);
  }

  /**
   * Producer's active route — lets them resume the sequence after closing/killing the app.
   *
   * <p>Self-heals on resume: reconciles stops whose ORDER already finished outside the stop flow
   * (customer-confirmed delivery, cancellation) and closes the route if all are terminal. Safety net
   * for {@link #syncStopForTerminalOrder}: covers stops left desynced by a race (producer + listener,
   * no lock/@Version on route entities) or by the listener's best-effort failure, preventing a
   * "ghost" route (ACTIVE with PENDING stops whose orders are already DELIVERED).
   */
  @Transactional
  public RouteResponse getActiveRoute(Jwt jwt) {
    User user = requireFarmer(jwt);
    DeliveryRoute route =
        deliveryRouteRepository
            .findByFarmerIdAndStatus(user.getId(), DeliveryRouteStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("Nenhuma rota ativa"));

    boolean changed = reconcileStopsWithOrders(route);
    boolean completed = completeIfAllStopsTerminal(route);
    if (changed || completed) {
      deliveryRouteRepository.saveAndFlush(route);
    }
    if (completed) {
      throw new NotFoundException("Nenhuma rota ativa");
    }
    return DeliveryRouteMapper.toResponse(route);
  }

  /**
   * Closes the previous active route before creating the new one. Reconciles stops first so a route
   * whose orders already finished closes as COMPLETED (honest history); only genuinely-open routes
   * become CANCELLED. The {@code saveAndFlush} is required: Hibernate runs INSERTs before UPDATEs, so
   * without it the new ACTIVE route would insert before this UPDATE, violating
   * {@code uq_delivery_routes_farmer_active}.
   */
  private void retirePreviousRoute(DeliveryRoute previous) {
    reconcileStopsWithOrders(previous);
    if (!completeIfAllStopsTerminal(previous)) {
      previous.setStatus(DeliveryRouteStatus.CANCELLED);
      previous.setCompletedAt(OffsetDateTime.now());
      // Position not shared outside an active delivery (privacy).
      trackingService.clearRoute(previous.getId());
    }
    deliveryRouteRepository.saveAndFlush(previous);
  }

  /**
   * Aligns each non-terminal stop to its ORDER's status when the order finished outside the stop flow
   * (customer-confirmed delivery, cancellation). Returns whether anything changed.
   */
  private boolean reconcileStopsWithOrders(DeliveryRoute route) {
    boolean changed = false;
    for (RouteStop stop : route.getStops()) {
      if (TERMINAL_STOP_STATUSES.contains(stop.getStatus())) {
        continue;
      }
      OrderStatus orderStatus = stop.getOrder().getStatus();
      if (orderStatus == OrderStatus.DELIVERED) {
        stop.setStatus(RouteStopStatus.DELIVERED);
        stop.setCompletedAt(OffsetDateTime.now());
        changed = true;
      } else if (orderStatus == OrderStatus.CANCELLED) {
        stop.setStatus(RouteStopStatus.FAILED);
        stop.setCompletedAt(OffsetDateTime.now());
        changed = true;
      }
    }
    return changed;
  }

  /** Closes the route if all stops are terminal (DELIVERED/FAILED) and stops tracking. */
  private boolean completeIfAllStopsTerminal(DeliveryRoute route) {
    boolean allDone =
        route.getStops().stream().allMatch(s -> TERMINAL_STOP_STATUSES.contains(s.getStatus()));
    if (allDone) {
      route.setStatus(DeliveryRouteStatus.COMPLETED);
      route.setCompletedAt(OffsetDateTime.now());
      // Position not shared outside an active delivery (privacy).
      trackingService.clearRoute(route.getId());
    }
    return allDone;
  }

  /**
   * Updates a stop's status (ARRIVED = arrival; DELIVERED completes the order via the state machine;
   * FAILED records a failed attempt without touching the order). When all stops are terminal the
   * route closes as COMPLETED. No Google calls here.
   */
  @Transactional(noRollbackFor = BusinessException.class)
  public RouteResponse updateStop(
      UUID routeId, UUID stopId, RouteStopStatus newStatus, String code, Jwt jwt) {
    User user = requireFarmer(jwt);

    DeliveryRoute route =
        deliveryRouteRepository
            .findWithStopsById(routeId)
            .orElseThrow(() -> new NotFoundException("Rota não encontrada"));
    if (!route.getFarmer().getId().equals(user.getId())) {
      throw new ForbiddenException("Você não tem permissão para acessar esta rota");
    }
    if (route.getStatus() != DeliveryRouteStatus.ACTIVE) {
      throw new BusinessException("A rota não está mais ativa");
    }

    RouteStop stop =
        route.getStops().stream()
            .filter(s -> s.getId().equals(stopId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Parada não encontrada nesta rota"));

    validateStopTransition(stop.getStatus(), newStatus);

    // Completing delivery requires the customer's code — delegates to confirmDeliveryWithCode (same
    // validation + 5/15min lockout). Validated BEFORE marking the stop: a wrong code throws and the
    // stop stays non-terminal, while the attempt counter still persists (this @Transactional's
    // noRollbackFor keeps the increment written inside).
    if (newStatus == RouteStopStatus.DELIVERED) {
      if (code == null || code.isBlank()) {
        throw new BusinessException("Código de confirmação obrigatório para concluir a entrega");
      }
      orderService.confirmDeliveryWithCode(stop.getOrder().getId(), code, jwt);
    }

    stop.setStatus(newStatus);
    if (TERMINAL_STOP_STATUSES.contains(newStatus)) {
      stop.setCompletedAt(OffsetDateTime.now());
    }

    boolean allDone =
        route.getStops().stream().allMatch(s -> TERMINAL_STOP_STATUSES.contains(s.getStatus()));
    if (allDone) {
      route.setStatus(DeliveryRouteStatus.COMPLETED);
      route.setCompletedAt(OffsetDateTime.now());
      // Position not shared outside an active delivery (privacy).
      trackingService.clearRoute(route.getId());
    }

    DeliveryRoute saved = deliveryRouteRepository.saveAndFlush(route);
    return DeliveryRouteMapper.toResponse(saved);
  }

  /**
   * Syncs the stop when the ORDER reaches a terminal state OUTSIDE the stop flow — the real case is
   * the customer confirming delivery via {@code POST /orders/customer/{id}/confirm-delivery} (touches
   * only the order). Without this the stop stays PENDING forever: the route never closes (becomes a
   * "ghost route") and the producer can't mark it later (terminal order rejects the transition).
   * DELIVERED → stop DELIVERED; CANCELLED → FAILED; if all terminal, the route closes.
   *
   * <p>Runs in its own transaction (REQUIRES_NEW) via an AFTER_COMMIT listener — best-effort: the
   * order is already confirmed/cancelled and nothing here can undo it. The "stop already terminal"
   * guard makes the producer path (which syncs in {@link #updateStop}) a no-op, avoiding reprocessing.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void syncStopForTerminalOrder(UUID orderId, OrderStatus orderStatus) {
    RouteStop located =
        routeStopRepository
            .findByOrderIdAndRouteStatus(orderId, DeliveryRouteStatus.ACTIVE)
            .orElse(null);
    if (located == null) {
      return; // order not on an active route — nothing to sync
    }

    DeliveryRoute route =
        deliveryRouteRepository.findWithStopsById(located.getRoute().getId()).orElse(null);
    if (route == null || route.getStatus() != DeliveryRouteStatus.ACTIVE) {
      return;
    }

    RouteStop stop =
        route.getStops().stream()
            .filter(s -> s.getOrder().getId().equals(orderId))
            .findFirst()
            .orElse(null);
    if (stop == null || TERMINAL_STOP_STATUSES.contains(stop.getStatus())) {
      return; // already synced by the producer flow — avoid reprocessing
    }

    stop.setStatus(
        orderStatus == OrderStatus.DELIVERED
            ? RouteStopStatus.DELIVERED
            : RouteStopStatus.FAILED);
    stop.setCompletedAt(OffsetDateTime.now());

    completeIfAllStopsTerminal(route);
    deliveryRouteRepository.saveAndFlush(route);
  }

  private void validateStopTransition(RouteStopStatus current, RouteStopStatus target) {
    boolean valid =
        switch (current) {
          case PENDING ->
              target == RouteStopStatus.ARRIVED
                  || target == RouteStopStatus.DELIVERED
                  || target == RouteStopStatus.FAILED;
          case ARRIVED -> target == RouteStopStatus.DELIVERED || target == RouteStopStatus.FAILED;
          case DELIVERED, FAILED -> false;
        };
    if (!valid) {
      throw new BusinessException(
          "Transição de parada inválida: %s → %s".formatted(current, target));
    }
  }

  /**
   * Stop coordinates: uses the snapshot frozen at purchase; if lat/lng are missing, geocodes the text
   * ONCE here so a failure becomes an actionable error instead of a paid geocode per route call.
   */
  private GeoPoint resolveCoordinates(Order order, AddressSnapshot snapshot, String addressText) {
    if (snapshot != null && snapshot.getLatitude() != null && snapshot.getLongitude() != null) {
      return new GeoPoint(snapshot.getLatitude(), snapshot.getLongitude());
    }
    // Accepts approximate (AMBIGUOUS) coordinates: enough to route. Fails only when Google returns NO
    // coordinate at all (nonexistent address) — then the route can't be built.
    GeocodeOutcome outcome = googleMapsService.geocode(addressText);
    if (!outcome.hasCoordinates()) {
      throw new BusinessException(
          "Não foi possível localizar o endereço de entrega do pedido "
              + order.getId()
              + " — confirme o endereço com o cliente");
    }
    return new GeoPoint(outcome.latitude(), outcome.longitude());
  }

  /** Baseline CO2: individual round trip to each stop (Route Matrix; haversine fallback). */
  private BigDecimal computeBaseline(GeoPoint origin, List<GeoPoint> points) {
    List<BigDecimal> distances = googleRoutesService.distancesFromOrigin(origin, points);
    BigDecimal oneWaySum;
    if (distances != null) {
      oneWaySum = distances.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    } else {
      oneWaySum =
          points.stream()
              .map(p -> haversineKm(origin, p))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    return oneWaySum.multiply(BigDecimal.valueOf(2)).setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal haversineKm(GeoPoint a, GeoPoint b) {
    double lat1 = Math.toRadians(a.latitude().doubleValue());
    double lat2 = Math.toRadians(b.latitude().doubleValue());
    double dLat = lat2 - lat1;
    double dLng = Math.toRadians(b.longitude().doubleValue() - a.longitude().doubleValue());
    double h =
        Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLng / 2), 2);
    return BigDecimal.valueOf(2 * 6371.0 * Math.asin(Math.sqrt(h)));
  }

  private static String snapshotText(AddressSnapshot snapshot) {
    if (snapshot == null) {
      return "";
    }
    return String.format(
        "%s, %s - %s, %s - %s, %s",
        snapshot.getStreet(),
        snapshot.getNumber(),
        snapshot.getNeighborhood(),
        snapshot.getCity(),
        snapshot.getState(),
        snapshot.getZipCode());
  }

  private User requireFarmer(Jwt jwt) {
    User user = userService.requireRole(jwt, TypeUser.FARMER, "Apenas produtores podem gerenciar rotas de entrega");
    return user;
  }
}
