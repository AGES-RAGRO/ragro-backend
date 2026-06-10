package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.config.TrackingProperties;
import br.com.ragro.controller.request.TrackingPositionMessage;
import br.com.ragro.controller.response.OrderTrackingResponse;
import br.com.ragro.controller.response.TrackingBroadcast;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.DeliveryRoute;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.Producer;
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
import br.com.ragro.service.api.PositionStore;
import br.com.ragro.service.api.PositionStore.LastPosition;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

  @Mock private DeliveryRouteRepository deliveryRouteRepository;
  @Mock private RouteStopRepository routeStopRepository;
  @Mock private RoutePositionRepository routePositionRepository;

  private final TrackingProperties properties = new TrackingProperties();
  private final FakePositionStore positionStore = new FakePositionStore();

  private TrackingService trackingService;

  private UUID routeId;
  private UUID farmerId;
  private DeliveryRoute route;
  private RouteStop stop;
  private Order order;

  /** Store simples em memória para os testes (sem Caffeine/expiração). */
  static class FakePositionStore implements PositionStore {
    final Map<UUID, LastPosition> map = new HashMap<>();

    @Override
    public void put(UUID routeId, LastPosition position) {
      map.put(routeId, position);
    }

    @Override
    public LastPosition get(UUID routeId) {
      return map.get(routeId);
    }

    @Override
    public void clear(UUID routeId) {
      map.remove(routeId);
    }
  }

  @BeforeEach
  void setUp() {
    trackingService =
        new TrackingService(
            properties,
            deliveryRouteRepository,
            routeStopRepository,
            routePositionRepository,
            positionStore);

    routeId = UUID.randomUUID();
    farmerId = UUID.randomUUID();

    Producer farmer = new Producer();
    farmer.setId(farmerId);

    User customerUser = new User();
    customerUser.setId(UUID.randomUUID());
    customerUser.setType(TypeUser.CUSTOMER);
    Customer customer = new Customer();
    customer.setId(customerUser.getId());
    customer.setUser(customerUser);

    order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);

    route = new DeliveryRoute();
    route.setId(routeId);
    route.setFarmer(farmer);
    route.setStatus(DeliveryRouteStatus.ACTIVE);
    route.setTotalDistanceKm(BigDecimal.TEN);
    route.setTotalDurationSeconds(1200);

    stop = new RouteStop();
    stop.setId(UUID.randomUUID());
    stop.setRoute(route);
    stop.setOrder(order);
    stop.setSequence(0);
    stop.setStatus(RouteStopStatus.PENDING);
    stop.setLatitude(BigDecimal.valueOf(-30.05));
    stop.setLongitude(BigDecimal.valueOf(-51.05));
    route.getStops().add(stop);
  }

  private TrackingPositionMessage ping(double lat, double lng, Double accuracy) {
    TrackingPositionMessage message = new TrackingPositionMessage();
    message.setLatitude(BigDecimal.valueOf(lat));
    message.setLongitude(BigDecimal.valueOf(lng));
    message.setAccuracyMeters(accuracy);
    return message;
  }

  @Test
  void ingestPosition_shouldStoreAndBroadcast_whenPingIsValid() {
    when(deliveryRouteRepository.findWithStopsById(routeId)).thenReturn(Optional.of(route));

    Optional<TrackingBroadcast> result =
        trackingService.ingestPosition(routeId, farmerId, ping(-30.0, -51.0, 10.0));

    assertThat(result).isPresent();
    assertThat(positionStore.get(routeId)).isNotNull();
    verify(routePositionRepository).save(any(RoutePosition.class));
    TrackingBroadcast broadcast = result.get();
    assertThat(broadcast.getEtas()).hasSize(1);
    assertThat(broadcast.getEtas().get(0).getOrderId()).isEqualTo(order.getId());
    assertThat(broadcast.getEtas().get(0).getEtaSeconds()).isPositive();
  }

  @Test
  void ingestPosition_shouldDiscardPing_whenAccuracyIsPoor() {
    when(deliveryRouteRepository.findWithStopsById(routeId)).thenReturn(Optional.of(route));

    Optional<TrackingBroadcast> result =
        trackingService.ingestPosition(routeId, farmerId, ping(-30.0, -51.0, 120.0));

    assertThat(result).isEmpty();
    verify(routePositionRepository, never()).save(any());
  }

  @Test
  void ingestPosition_shouldDiscardImpossibleJump() {
    when(deliveryRouteRepository.findWithStopsById(routeId)).thenReturn(Optional.of(route));
    // Última posição aceita há 1s, a ~111 km de distância (1 grau de latitude) → ~400.000 km/h.
    positionStore.put(
        routeId,
        new LastPosition(
            BigDecimal.valueOf(-31.0),
            BigDecimal.valueOf(-51.0),
            OffsetDateTime.now().minusSeconds(1),
            null));
    properties.setMinIntervalMs(100);

    Optional<TrackingBroadcast> result =
        trackingService.ingestPosition(routeId, farmerId, ping(-30.0, -51.0, 10.0));

    assertThat(result).isEmpty();
  }

  @Test
  void ingestPosition_shouldRejectPing_whenSenderIsNotRouteOwner() {
    when(deliveryRouteRepository.findWithStopsById(routeId)).thenReturn(Optional.of(route));

    Optional<TrackingBroadcast> result =
        trackingService.ingestPosition(routeId, UUID.randomUUID(), ping(-30.0, -51.0, 10.0));

    assertThat(result).isEmpty();
    verify(routePositionRepository, never()).save(any());
  }

  @Test
  void ingestPosition_shouldRespectMinInterval() {
    when(deliveryRouteRepository.findWithStopsById(routeId)).thenReturn(Optional.of(route));

    assertThat(trackingService.ingestPosition(routeId, farmerId, ping(-30.0, -51.0, 10.0)))
        .isPresent();
    // Segundo ping imediato: descartado pelo intervalo mínimo (default 2s).
    assertThat(trackingService.ingestPosition(routeId, farmerId, ping(-30.001, -51.0, 10.0)))
        .isEmpty();
  }

  @Test
  void ingestPosition_shouldIgnore_whenRouteNotActive() {
    route.setStatus(DeliveryRouteStatus.COMPLETED);
    when(deliveryRouteRepository.findWithStopsById(routeId)).thenReturn(Optional.of(route));

    assertThat(trackingService.ingestPosition(routeId, farmerId, ping(-30.0, -51.0, 10.0)))
        .isEmpty();
  }

  @Test
  void trackingForOrder_shouldReturnSnapshotWithEta_forOwningCustomer() {
    when(routeStopRepository.findByOrderIdAndRouteStatus(
            order.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.of(stop));
    positionStore.put(
        routeId,
        new LastPosition(
            BigDecimal.valueOf(-30.0), BigDecimal.valueOf(-51.0), OffsetDateTime.now(), null));

    OrderTrackingResponse response =
        trackingService.trackingForOrder(order.getId(), order.getCustomer().getUser());

    assertThat(response.isAvailable()).isTrue();
    assertThat(response.getRouteId()).isEqualTo(routeId);
    assertThat(response.getProducerLatitude()).isEqualByComparingTo("-30.0");
    assertThat(response.getEtaSeconds()).isPositive();
    assertThat(response.getDestinationLatitude()).isEqualByComparingTo("-30.05");
  }

  @Test
  void trackingForOrder_shouldThrowForbidden_whenNotTheOrderCustomer() {
    when(routeStopRepository.findByOrderIdAndRouteStatus(
            order.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.of(stop));

    User otherCustomer = new User();
    otherCustomer.setId(UUID.randomUUID());
    otherCustomer.setType(TypeUser.CUSTOMER);

    assertThatThrownBy(() -> trackingService.trackingForOrder(order.getId(), otherCustomer))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void trackingForOrder_shouldReturnUnavailable_whenOrderNotOnActiveRoute() {
    when(routeStopRepository.findByOrderIdAndRouteStatus(any(), any()))
        .thenReturn(Optional.empty());

    OrderTrackingResponse response =
        trackingService.trackingForOrder(UUID.randomUUID(), order.getCustomer().getUser());

    assertThat(response.isAvailable()).isFalse();
  }

  @Test
  void trackingForOrder_shouldReturnUnavailable_whenTrackingDisabled() {
    properties.setEnabled(false);

    OrderTrackingResponse response =
        trackingService.trackingForOrder(order.getId(), order.getCustomer().getUser());

    assertThat(response.isAvailable()).isFalse();
  }

  @Test
  void canSubscribe_shouldAllowRouteFarmerAndRouteCustomerOnly() {
    when(deliveryRouteRepository.findById(routeId)).thenReturn(Optional.of(route));
    when(routeStopRepository.existsByRouteIdAndOrderCustomerId(
            routeId, order.getCustomer().getId()))
        .thenReturn(true);
    UUID strangerId = UUID.randomUUID();
    when(routeStopRepository.existsByRouteIdAndOrderCustomerId(routeId, strangerId))
        .thenReturn(false);

    assertThat(trackingService.canSubscribe(routeId, farmerId, TypeUser.FARMER)).isTrue();
    assertThat(
            trackingService.canSubscribe(
                routeId, order.getCustomer().getId(), TypeUser.CUSTOMER))
        .isTrue();
    assertThat(trackingService.canSubscribe(routeId, strangerId, TypeUser.CUSTOMER)).isFalse();
    assertThat(trackingService.canSubscribe(routeId, UUID.randomUUID(), TypeUser.FARMER))
        .isFalse();
  }

  @Test
  void clearRoute_shouldDropLastPosition() {
    positionStore.put(
        routeId,
        new LastPosition(BigDecimal.ONE, BigDecimal.ONE, OffsetDateTime.now(), null));

    trackingService.clearRoute(routeId);

    assertThat(positionStore.get(routeId)).isNull();
  }
}
