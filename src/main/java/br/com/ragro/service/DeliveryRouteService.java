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
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rota de entrega persistida (plano Etapa 1, Fase 2). Substitui o {@code POST /routes/optimize}
 * efêmero: a rota é calculada UMA vez no Google (round trip otimizado, com trânsito), persistida
 * com paradas ordenadas/ETA por leg, e as confirmações de entrega passam a ser PATCH por parada —
 * sem novas chamadas ao Google (antes, cada confirmação recalculava e pagava a rota inteira).
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
   * Cria a rota ativa do produtor a partir dos pedidos CONFIRMED/IN_DELIVERY. Uma rota ativa
   * anterior é fechada antes ({@link #retirePreviousRoute}) — reabrir a tela e recriar é o fluxo
   * natural quando novos pedidos são confirmados. Pedidos CONFIRMED transitam para IN_DELIVERY
   * pela máquina de estados (com notificação ao cliente via evento).
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

    // Saiu para entrega: pedidos confirmados transitam para IN_DELIVERY (efeitos + notificação
    // pela máquina de estados do OrderService — antes o app pulava direto p/ DELIVERED).
    for (Order order : orders) {
      if (order.getStatus() == OrderStatus.CONFIRMED) {
        orderService.updateOrderStatus(order.getId(), OrderStatus.IN_DELIVERY, jwt);
      }
    }

    return DeliveryRouteMapper.toResponse(saved);
  }

  /**
   * Rota ativa do produtor — permite retomar a sequência depois de fechar/matar o app.
   *
   * <p>Faz <b>self-heal</b> ao resumir: reconcilia paradas cujo PEDIDO já terminou por fora do
   * fluxo de parada (cliente confirmou a entrega, cancelamento) e fecha a rota se todas terminaram.
   * É a rede de segurança do {@link #syncStopForTerminalOrder}: cobre qualquer parada que tenha
   * ficado dessincronizada por corrida (produtor + listener simultâneos, sem lock/@Version nas
   * entidades de rota) ou por falha best-effort do listener — sem isto a rota voltava como
   * "fantasma" aqui (ACTIVE com paradas presas em PENDING cujos pedidos já estavam DELIVERED).
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
   * Fecha a rota ativa anterior antes de criar a nova. Reconcilia as paradas primeiro para que uma
   * rota cujos pedidos já terminaram feche como COMPLETED (histórico honesto); só o que sobrou
   * genuinamente aberto vira CANCELLED. O {@code saveAndFlush} aqui é obrigatório, não cosmético:
   * no flush o Hibernate executa INSERTs antes de UPDATEs, então sem o flush imediato a nova rota
   * ACTIVE entraria no banco antes do UPDATE desta, violando {@code
   * uq_delivery_routes_farmer_active}.
   */
  private void retirePreviousRoute(DeliveryRoute previous) {
    reconcileStopsWithOrders(previous);
    if (!completeIfAllStopsTerminal(previous)) {
      previous.setStatus(DeliveryRouteStatus.CANCELLED);
      previous.setCompletedAt(OffsetDateTime.now());
      // Fora de entrega ativa a posição não é compartilhada (privacidade).
      trackingService.clearRoute(previous.getId());
    }
    deliveryRouteRepository.saveAndFlush(previous);
  }

  /**
   * Alinha cada parada não-terminal ao status do PEDIDO quando ele terminou por fora do fluxo de
   * parada (cliente confirmou a entrega, cancelamento). Retorna se algo mudou.
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

  /** Fecha a rota se todas as paradas estão terminais (DELIVERED/FAILED) e encerra o tracking. */
  private boolean completeIfAllStopsTerminal(DeliveryRoute route) {
    boolean allDone =
        route.getStops().stream().allMatch(s -> TERMINAL_STOP_STATUSES.contains(s.getStatus()));
    if (allDone) {
      route.setStatus(DeliveryRouteStatus.COMPLETED);
      route.setCompletedAt(OffsetDateTime.now());
      // Fora de entrega ativa a posição não é compartilhada (privacidade).
      trackingService.clearRoute(route.getId());
    }
    return allDone;
  }

  /**
   * Atualiza o status de uma parada (ARRIVED → chegada; DELIVERED conclui o pedido pela máquina
   * de estados; FAILED registra tentativa frustrada sem mexer no pedido). Quando todas as paradas
   * terminam, a rota fecha como COMPLETED. Nenhuma chamada ao Google acontece aqui.
   */
  @Transactional
  public RouteResponse updateStop(UUID routeId, UUID stopId, RouteStopStatus newStatus, Jwt jwt) {
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
    stop.setStatus(newStatus);
    if (TERMINAL_STOP_STATUSES.contains(newStatus)) {
      stop.setCompletedAt(OffsetDateTime.now());
    }

    if (newStatus == RouteStopStatus.DELIVERED) {
      orderService.updateOrderStatus(stop.getOrder().getId(), OrderStatus.DELIVERED, jwt);
    }

    boolean allDone =
        route.getStops().stream().allMatch(s -> TERMINAL_STOP_STATUSES.contains(s.getStatus()));
    if (allDone) {
      route.setStatus(DeliveryRouteStatus.COMPLETED);
      route.setCompletedAt(OffsetDateTime.now());
      // Fora de entrega ativa a posição não é compartilhada (privacidade).
      trackingService.clearRoute(route.getId());
    }

    DeliveryRoute saved = deliveryRouteRepository.saveAndFlush(route);
    return DeliveryRouteMapper.toResponse(saved);
  }

  /**
   * Sincroniza a parada quando o PEDIDO chega a um estado terminal por FORA do fluxo de parada — o
   * caso real é o cliente confirmando a entrega em {@code POST /orders/customer/{id}/confirm-delivery}
   * (mexe só no pedido). Sem isto a parada ficava PENDING para sempre: a rota nunca fechava (virava
   * "rota fantasma" no {@code GET /routes/active}) e o produtor nem conseguia marcar a parada depois
   * (o pedido já terminal recusava a transição). DELIVERED → parada DELIVERED; CANCELLED → FAILED;
   * se todas terminam, a rota fecha.
   *
   * <p>Roda em transação própria (REQUIRES_NEW), disparada por um listener AFTER_COMMIT — é
   * best-effort: o pedido já foi confirmado/cancelado e nada aqui pode desfazer isso. O guard de
   * "parada já terminal" torna o caminho do produtor (que já sincroniza tudo em {@link #updateStop})
   * um no-op, evitando reprocessamento.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void syncStopForTerminalOrder(UUID orderId, OrderStatus orderStatus) {
    RouteStop located =
        routeStopRepository
            .findByOrderIdAndRouteStatus(orderId, DeliveryRouteStatus.ACTIVE)
            .orElse(null);
    if (located == null) {
      return; // pedido não está numa rota ativa — nada a sincronizar
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
      return; // já sincronizada pelo fluxo do produtor — evita reprocessar
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
   * Coordenadas da parada: usa o snapshot congelado na compra; sem lat/lng (endereço cadastrado
   * com a key ausente, p.ex.), geocodifica o texto UMA vez aqui — falha vira erro acionável em
   * vez do geocode embutido (e cobrado) dentro de cada chamada de rota, como era antes.
   */
  private GeoPoint resolveCoordinates(Order order, AddressSnapshot snapshot, String addressText) {
    if (snapshot != null && snapshot.getLatitude() != null && snapshot.getLongitude() != null) {
      return new GeoPoint(snapshot.getLatitude(), snapshot.getLongitude());
    }
    // Aceita coordenada aproximada (AMBIGUOUS): suficiente para rotear. Só falha quando o Google
    // não devolve NENHUMA coordenada (endereço inexistente) — aí a rota não tem como ser montada.
    GeocodeOutcome outcome = googleMapsService.geocode(addressText);
    if (!outcome.hasCoordinates()) {
      throw new BusinessException(
          "Não foi possível localizar o endereço de entrega do pedido "
              + order.getId()
              + " — confirme o endereço com o cliente");
    }
    return new GeoPoint(outcome.latitude(), outcome.longitude());
  }

  /** Baseline CO2: ida-e-volta individual a cada parada (Route Matrix; fallback haversine). */
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
