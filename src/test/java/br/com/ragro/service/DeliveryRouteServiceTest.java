package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.CreateRouteRequest;
import br.com.ragro.controller.response.RouteResponse;
import br.com.ragro.domain.AddressSnapshot;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.DeliveryRoute;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.RouteStop;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.DeliveryRouteStatus;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.RouteStopStatus;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.DeliveryRouteRepository;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.RouteStopRepository;
import br.com.ragro.service.GoogleRoutesService.ComputedRoute;
import br.com.ragro.service.GoogleRoutesService.RouteLeg;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class DeliveryRouteServiceTest {

  @Mock private UserService userService;
  @Mock private OrderRepository orderRepository;
  @Mock private DeliveryRouteRepository deliveryRouteRepository;
  @Mock private RouteStopRepository routeStopRepository;
  @Mock private GoogleRoutesService googleRoutesService;
  @Mock private GoogleMapsService googleMapsService;
  @Mock private OrderService orderService;
  @Mock private TrackingService trackingService;

  @InjectMocks private DeliveryRouteService deliveryRouteService;

  private User farmerUser;
  private Producer farmer;

  @BeforeEach
  void setUp() {
    farmerUser = new User();
    farmerUser.setId(UUID.randomUUID());
    farmerUser.setType(TypeUser.FARMER);
    farmer = new Producer();
    farmer.setId(farmerUser.getId());
  }

  private Jwt jwt() {
    return Jwt.withTokenValue("token").header("alg", "none").claim("sub", "sub").build();
  }

  /** requireRole returns the authenticated producer; any other type throws ForbiddenException. */
  private void stubFarmerAuthenticated() {
    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
  }

  private Order order(OrderStatus status, double lat, double lng) {
    User customerUser = new User();
    customerUser.setId(UUID.randomUUID());
    customerUser.setName("Cliente");
    Customer customer = new Customer();
    customer.setId(customerUser.getId());
    customer.setUser(customerUser);

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setFarmer(farmer);
    order.setCustomer(customer);
    order.setStatus(status);
    order.setDeliveryAddressSnapshot(
        AddressSnapshot.builder()
            .street("Rua A")
            .number("1")
            .city("Porto Alegre")
            .state("RS")
            .zipCode("90000000")
            .latitude(BigDecimal.valueOf(lat))
            .longitude(BigDecimal.valueOf(lng))
            .build());
    return order;
  }

  private CreateRouteRequest request() {
    CreateRouteRequest request = new CreateRouteRequest();
    request.setOriginLatitude(BigDecimal.valueOf(-30.0));
    request.setOriginLongitude(BigDecimal.valueOf(-51.0));
    return request;
  }

  @Test
  void createRoute_shouldPersistOptimizedStopsAndMoveOrdersToInDelivery() {
    Order first = order(OrderStatus.CONFIRMED, -30.1, -51.1);
    Order second = order(OrderStatus.IN_DELIVERY, -30.2, -51.2);

    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findByFarmerIdAndStatus(
            farmerUser.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.empty());
    when(orderRepository.findByFarmerIdAndStatusInOrderByCreatedAtAsc(eq(farmerUser.getId()), any()))
        .thenReturn(List.of(first, second));
    // Optimization reverses visit order: input's second stop is visited first.
    when(googleRoutesService.computeOptimizedRoundTrip(any(), any()))
        .thenReturn(
            new ComputedRoute(
                BigDecimal.valueOf(12.5),
                1800,
                "poly123",
                List.of(1, 0),
                List.of(
                    new RouteLeg(BigDecimal.valueOf(5.0), 600),
                    new RouteLeg(BigDecimal.valueOf(4.0), 500),
                    new RouteLeg(BigDecimal.valueOf(3.5), 700))));
    when(googleRoutesService.distancesFromOrigin(any(), any()))
        .thenReturn(List.of(BigDecimal.valueOf(5.0), BigDecimal.valueOf(6.0)));
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RouteResponse response = deliveryRouteService.createRoute(request(), jwt());

    assertThat(response.getStatus()).isEqualTo(DeliveryRouteStatus.ACTIVE);
    assertThat(response.getTotalDistanceKm()).isEqualByComparingTo("12.5");
    assertThat(response.getOverviewPolyline()).isEqualTo("poly123");
    // Baseline = 2 x (5 + 6) via Route Matrix.
    assertThat(response.getBaselineDistanceKm()).isEqualByComparingTo("22.00");
    assertThat(response.getStops()).hasSize(2);
    // Optimized order: "second" is stop 0.
    assertThat(response.getStops().get(0).getOrderId()).isEqualTo(second.getId());
    assertThat(response.getStops().get(0).getLegDurationSeconds()).isEqualTo(600);
    assertThat(response.getStops().get(1).getOrderId()).isEqualTo(first.getId());
    assertThat(response.getStops().get(1).getEta())
        .isAfter(response.getStops().get(0).getEta());
    // Only CONFIRMED transitions; an already-IN_DELIVERY order is not re-transitioned.
    verify(orderService).updateOrderStatus(first.getId(), OrderStatus.IN_DELIVERY, jwt());
    verify(orderService, never())
        .updateOrderStatus(eq(second.getId()), eq(OrderStatus.IN_DELIVERY), any());
  }

  @Test
  void createRoute_shouldThrowBusinessException_whenOptimizedOrderIndexOutOfRange() {
    // A corrupt optimizedOrder index (5 with a single order) must surface a BusinessException,
    // not an IndexOutOfBoundsException (500) when indexing orders.get(inputIndex).
    Order single = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);

    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findByFarmerIdAndStatus(any(), any()))
        .thenReturn(Optional.empty());
    when(orderRepository.findByFarmerIdAndStatusInOrderByCreatedAtAsc(any(), any()))
        .thenReturn(List.of(single));
    // Single order (index 0 valid), but the optimizer hands back index 5 -> out of range.
    when(googleRoutesService.computeOptimizedRoundTrip(any(), any()))
        .thenReturn(
            new ComputedRoute(
                BigDecimal.ONE,
                60,
                "poly",
                List.of(5),
                List.of(new RouteLeg(BigDecimal.ONE, 60), new RouteLeg(BigDecimal.ONE, 60))));

    assertThatThrownBy(() -> deliveryRouteService.createRoute(request(), jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("índice fora do intervalo");
  }

  @Test
  void createRoute_shouldCancelPreviousActiveRoute() {
    Order stillOpen = order(OrderStatus.IN_DELIVERY, -30.3, -51.3);
    DeliveryRoute previous = activeRouteWithStops(stop(stillOpen, RouteStopStatus.PENDING));

    Order single = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findByFarmerIdAndStatus(
            farmerUser.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.of(previous));
    when(orderRepository.findByFarmerIdAndStatusInOrderByCreatedAtAsc(eq(farmerUser.getId()), any()))
        .thenReturn(List.of(single));
    when(googleRoutesService.computeOptimizedRoundTrip(any(), any()))
        .thenReturn(
            new ComputedRoute(
                BigDecimal.ONE,
                60,
                null,
                List.of(0),
                List.of(new RouteLeg(BigDecimal.ONE, 60), new RouteLeg(BigDecimal.ONE, 60))));
    when(googleRoutesService.distancesFromOrigin(any(), any())).thenReturn(null);
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    deliveryRouteService.createRoute(request(), jwt());

    assertThat(previous.getStatus()).isEqualTo(DeliveryRouteStatus.CANCELLED);
    assertThat(previous.getCompletedAt()).isNotNull();
    verify(trackingService).clearRoute(previous.getId());
    // Previous route must hit the DB before the new INSERT: Hibernate flushes INSERTs before
    // UPDATEs, so without this flush the new ACTIVE violates uq_delivery_routes_farmer_active.
    org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(deliveryRouteRepository);
    inOrder.verify(deliveryRouteRepository).saveAndFlush(previous);
    inOrder
        .verify(deliveryRouteRepository)
        .saveAndFlush(org.mockito.ArgumentMatchers.argThat(r -> r != previous));
  }

  @Test
  void createRoute_shouldCompletePreviousRoute_whenAllItsOrdersAlreadyTerminal() {
    // Ghost-route case: PENDING stop whose order the customer already confirmed (DELIVERED).
    // Recreating the route reconciles the previous one, closing it as COMPLETED, not CANCELLED.
    Order alreadyDelivered = order(OrderStatus.DELIVERED, -30.3, -51.3);
    DeliveryRoute previous = activeRouteWithStops(stop(alreadyDelivered, RouteStopStatus.PENDING));

    Order single = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findByFarmerIdAndStatus(
            farmerUser.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.of(previous));
    when(orderRepository.findByFarmerIdAndStatusInOrderByCreatedAtAsc(eq(farmerUser.getId()), any()))
        .thenReturn(List.of(single));
    when(googleRoutesService.computeOptimizedRoundTrip(any(), any()))
        .thenReturn(
            new ComputedRoute(
                BigDecimal.ONE,
                60,
                null,
                List.of(0),
                List.of(new RouteLeg(BigDecimal.ONE, 60), new RouteLeg(BigDecimal.ONE, 60))));
    when(googleRoutesService.distancesFromOrigin(any(), any())).thenReturn(null);
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RouteResponse response = deliveryRouteService.createRoute(request(), jwt());

    assertThat(previous.getStatus()).isEqualTo(DeliveryRouteStatus.COMPLETED);
    assertThat(previous.getCompletedAt()).isNotNull();
    assertThat(previous.getStops().get(0).getStatus()).isEqualTo(RouteStopStatus.DELIVERED);
    assertThat(previous.getStops().get(0).getCompletedAt()).isNotNull();
    verify(trackingService).clearRoute(previous.getId());
    assertThat(response.getStatus()).isEqualTo(DeliveryRouteStatus.ACTIVE);
    org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(deliveryRouteRepository);
    inOrder.verify(deliveryRouteRepository).saveAndFlush(previous);
    inOrder
        .verify(deliveryRouteRepository)
        .saveAndFlush(org.mockito.ArgumentMatchers.argThat(r -> r != previous));
  }

  @Test
  void createRoute_shouldThrowBusinessException_whenNoDeliverableOrders() {
    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findByFarmerIdAndStatus(any(), any()))
        .thenReturn(Optional.empty());
    when(orderRepository.findByFarmerIdAndStatusInOrderByCreatedAtAsc(any(), any()))
        .thenReturn(List.of());

    assertThatThrownBy(() -> deliveryRouteService.createRoute(request(), jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Nenhum pedido");
  }

  @Test
  void createRoute_shouldThrowForbidden_whenUserIsNotFarmer() {
    farmerUser.setType(TypeUser.CUSTOMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });

    assertThatThrownBy(() -> deliveryRouteService.createRoute(request(), jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("produtores");
  }

  @Test
  void addStops_shouldReoptimizeAddingNewOrder_keepingExistingStopAndCo2Delta() {
    Order existing = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    RouteStop existingStop = stop(existing, RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(existingStop);
    route.setBaselineDistanceKm(BigDecimal.valueOf(10));

    Order newOrder = order(OrderStatus.CONFIRMED, -30.2, -51.2);

    stubFarmerAuthenticated();
    when(deliveryRouteRepository.findWithStopsById(route.getId()))
        .thenReturn(Optional.of(route));
    when(orderRepository.findByFarmerIdAndStatusInOrderByCreatedAtAsc(
            eq(farmerUser.getId()), any()))
        .thenReturn(List.of(newOrder));
    when(googleRoutesService.computeOptimizedRoundTrip(any(), any()))
        .thenReturn(
            new ComputedRoute(
                BigDecimal.valueOf(20.0),
                2000,
                "poly2",
                List.of(0, 1),
                List.of(
                    new RouteLeg(BigDecimal.valueOf(5.0), 600),
                    new RouteLeg(BigDecimal.valueOf(4.0), 500),
                    new RouteLeg(BigDecimal.valueOf(3.5), 700))));
    when(googleRoutesService.distancesFromOrigin(any(), any()))
        .thenReturn(List.of(BigDecimal.valueOf(4.0)));
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RouteResponse response = deliveryRouteService.addStops(route.getId(), jwt());

    assertThat(response.getStops()).hasSize(2);
    assertThat(response.getStops())
        .anyMatch(s -> s.getOrderId().equals(newOrder.getId()));
    assertThat(response.getStops())
        .anyMatch(s -> s.getOrderId().equals(existing.getId()));
    assertThat(response.getTotalDistanceKm()).isEqualByComparingTo("20.0");
    assertThat(response.getOverviewPolyline()).isEqualTo("poly2");
    // Baseline grows by the new order's round-trip delta only (10 + 2*4 = 18), no double count.
    assertThat(response.getBaselineDistanceKm()).isEqualByComparingTo("18.00");
    // Only the NEW order moves to IN_DELIVERY; the existing in-delivery order is untouched.
    verify(orderService).updateOrderStatus(newOrder.getId(), OrderStatus.IN_DELIVERY, jwt());
    verify(orderService, never())
        .updateOrderStatus(eq(existing.getId()), any(), any());
  }

  @Test
  void addStops_shouldBeNoOp_whenNoNewOrders() {
    Order existing = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    DeliveryRoute route = activeRouteWithStops(stop(existing, RouteStopStatus.PENDING));

    stubFarmerAuthenticated();
    when(deliveryRouteRepository.findWithStopsById(route.getId()))
        .thenReturn(Optional.of(route));
    // No CONFIRMED orders to add.
    when(orderRepository.findByFarmerIdAndStatusInOrderByCreatedAtAsc(
            eq(farmerUser.getId()), any()))
        .thenReturn(List.of());

    RouteResponse response = deliveryRouteService.addStops(route.getId(), jwt());

    assertThat(response.getStops()).hasSize(1);
    // No re-optimization (no Google call) and no order transitions when nothing is new.
    verify(googleRoutesService, never()).computeOptimizedRoundTrip(any(), any());
    verify(orderService, never()).updateOrderStatus(any(), any(), any());
  }

  @Test
  void addStops_shouldThrowBusinessException_whenRouteNotActive() {
    Order existing = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    DeliveryRoute route = activeRouteWithStops(stop(existing, RouteStopStatus.PENDING));
    route.setStatus(DeliveryRouteStatus.COMPLETED);

    stubFarmerAuthenticated();
    when(deliveryRouteRepository.findWithStopsById(route.getId()))
        .thenReturn(Optional.of(route));

    assertThatThrownBy(() -> deliveryRouteService.addStops(route.getId(), jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("não está mais ativa");
  }

  private DeliveryRoute activeRouteWithStops(RouteStop... stops) {
    DeliveryRoute route = new DeliveryRoute();
    route.setId(UUID.randomUUID());
    route.setFarmer(farmer);
    route.setStatus(DeliveryRouteStatus.ACTIVE);
    for (RouteStop stop : stops) {
      stop.setRoute(route);
      route.getStops().add(stop);
    }
    return route;
  }

  private RouteStop stop(Order order, RouteStopStatus status) {
    RouteStop stop = new RouteStop();
    stop.setId(UUID.randomUUID());
    stop.setOrder(order);
    stop.setStatus(status);
    stop.setLatitude(BigDecimal.ONE);
    stop.setLongitude(BigDecimal.ONE);
    return stop;
  }

  @Test
  void syncStop_shouldDeliverStopAndCompleteRoute_whenCustomerConfirmedOnlyDelivery() {
    // Customer confirmed delivery via the order (stop stayed PENDING): sync completes the stop
    // and, being the only one, closes the route (it used to stay ACTIVE forever = ghost route).
    Order delivered = order(OrderStatus.DELIVERED, -30.1, -51.1);
    RouteStop pending = stop(delivered, RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(pending);

    when(routeStopRepository.findByOrderIdAndRouteStatus(
            delivered.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.of(pending));
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    deliveryRouteService.syncStopForTerminalOrder(delivered.getId(), OrderStatus.DELIVERED);

    assertThat(pending.getStatus()).isEqualTo(RouteStopStatus.DELIVERED);
    assertThat(pending.getCompletedAt()).isNotNull();
    assertThat(route.getStatus()).isEqualTo(DeliveryRouteStatus.COMPLETED);
    verify(trackingService).clearRoute(route.getId());
    verify(deliveryRouteRepository).saveAndFlush(route);
  }

  @Test
  void syncStop_shouldKeepRouteActive_whenOtherStopsStillPending() {
    Order delivered = order(OrderStatus.DELIVERED, -30.1, -51.1);
    RouteStop confirmed = stop(delivered, RouteStopStatus.PENDING);
    RouteStop otherPending =
        stop(order(OrderStatus.IN_DELIVERY, -30.2, -51.2), RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(confirmed, otherPending);

    when(routeStopRepository.findByOrderIdAndRouteStatus(
            delivered.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.of(confirmed));
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    deliveryRouteService.syncStopForTerminalOrder(delivered.getId(), OrderStatus.DELIVERED);

    assertThat(confirmed.getStatus()).isEqualTo(RouteStopStatus.DELIVERED);
    assertThat(route.getStatus()).isEqualTo(DeliveryRouteStatus.ACTIVE);
    verify(trackingService, never()).clearRoute(any());
  }

  @Test
  void syncStop_shouldFailStop_whenOrderCancelled() {
    Order cancelled = order(OrderStatus.CANCELLED, -30.1, -51.1);
    RouteStop pending = stop(cancelled, RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(pending);

    when(routeStopRepository.findByOrderIdAndRouteStatus(
            cancelled.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.of(pending));
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    deliveryRouteService.syncStopForTerminalOrder(cancelled.getId(), OrderStatus.CANCELLED);

    assertThat(pending.getStatus()).isEqualTo(RouteStopStatus.FAILED);
    assertThat(route.getStatus()).isEqualTo(DeliveryRouteStatus.COMPLETED);
  }

  @Test
  void syncStop_shouldBeNoOp_whenStopAlreadyTerminal() {
    // Producer path (updateStop) already completed the stop and fired the event; re-entry must
    // not reprocess: stop already DELIVERED -> nothing to save.
    Order delivered = order(OrderStatus.DELIVERED, -30.1, -51.1);
    RouteStop already = stop(delivered, RouteStopStatus.DELIVERED);
    RouteStop otherPending =
        stop(order(OrderStatus.IN_DELIVERY, -30.2, -51.2), RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(already, otherPending);

    when(routeStopRepository.findByOrderIdAndRouteStatus(
            delivered.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.of(already));
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));

    deliveryRouteService.syncStopForTerminalOrder(delivered.getId(), OrderStatus.DELIVERED);

    verify(deliveryRouteRepository, never()).saveAndFlush(any());
    verify(trackingService, never()).clearRoute(any());
  }

  @Test
  void syncStop_shouldBeNoOp_whenOrderNotInActiveRoute() {
    UUID orderId = UUID.randomUUID();
    when(routeStopRepository.findByOrderIdAndRouteStatus(orderId, DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.empty());

    deliveryRouteService.syncStopForTerminalOrder(orderId, OrderStatus.DELIVERED);

    verify(deliveryRouteRepository, never()).findWithStopsById(any());
    verify(deliveryRouteRepository, never()).saveAndFlush(any());
  }

  @Test
  void getActiveRoute_shouldSelfHealAndComplete_whenStopOrderTerminalButStopPending() {
    // Safety net: stop stuck PENDING with order already DELIVERED (lost listener/race). On resume,
    // reconcile and close the route; no active route remains (the "ghost" is gone).
    Order delivered = order(OrderStatus.DELIVERED, -30.1, -51.1);
    RouteStop stale = stop(delivered, RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(stale);

    when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(farmerUser);
    when(deliveryRouteRepository.findByFarmerIdAndStatus(
            farmerUser.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.of(route));
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(() -> deliveryRouteService.getActiveRoute(jwt()))
        .isInstanceOf(NotFoundException.class);

    assertThat(stale.getStatus()).isEqualTo(RouteStopStatus.DELIVERED);
    assertThat(route.getStatus()).isEqualTo(DeliveryRouteStatus.COMPLETED);
    verify(trackingService).clearRoute(route.getId());
  }

  @Test
  void getActiveRoute_shouldReconcileButStayActive_whenAnotherStopStillDeliverable() {
    Order delivered = order(OrderStatus.DELIVERED, -30.1, -51.1);
    RouteStop stale = stop(delivered, RouteStopStatus.PENDING);
    RouteStop stillPending =
        stop(order(OrderStatus.IN_DELIVERY, -30.2, -51.2), RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(stale, stillPending);

    when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(farmerUser);
    when(deliveryRouteRepository.findByFarmerIdAndStatus(
            farmerUser.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.of(route));
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RouteResponse response = deliveryRouteService.getActiveRoute(jwt());

    assertThat(response.getStatus()).isEqualTo(DeliveryRouteStatus.ACTIVE);
    assertThat(stale.getStatus()).isEqualTo(RouteStopStatus.DELIVERED); // reconciled
    assertThat(stillPending.getStatus()).isEqualTo(RouteStopStatus.PENDING); // still deliverable
    verify(deliveryRouteRepository).saveAndFlush(route); // reconciliation persisted
    verify(trackingService, never()).clearRoute(any());
  }

  @Test
  void getActiveRoute_shouldReturnAsIs_whenNothingToReconcile() {
    RouteStop pending = stop(order(OrderStatus.IN_DELIVERY, -30.1, -51.1), RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(pending);

    when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(farmerUser);
    when(deliveryRouteRepository.findByFarmerIdAndStatus(
            farmerUser.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.of(route));

    RouteResponse response = deliveryRouteService.getActiveRoute(jwt());

    assertThat(response.getStatus()).isEqualTo(DeliveryRouteStatus.ACTIVE);
    verify(deliveryRouteRepository, never()).saveAndFlush(any());
  }

  @Test
  void updateStop_shouldDeliverOrderAndCompleteRoute_whenLastStopDelivered() {
    Order order = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    RouteStop pending = stop(order, RouteStopStatus.PENDING);
    RouteStop done = stop(order(OrderStatus.DELIVERED, -30.2, -51.2), RouteStopStatus.DELIVERED);
    DeliveryRoute route = activeRouteWithStops(pending, done);

    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RouteResponse response =
        deliveryRouteService.updateStop(
            route.getId(), pending.getId(), RouteStopStatus.DELIVERED, "1234", jwt());

    // Completing the stop delegates to the hardened path (code + lockout), not to
    // updateOrderStatus(...DELIVERED...) which bypassed delivery confirmation.
    verify(orderService).confirmDeliveryWithCode(order.getId(), "1234", jwt());
    verify(orderService, never()).updateOrderStatus(any(), eq(OrderStatus.DELIVERED), any());
    assertThat(pending.getStatus()).isEqualTo(RouteStopStatus.DELIVERED);
    assertThat(pending.getCompletedAt()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(DeliveryRouteStatus.COMPLETED);
  }

  @Test
  void updateStop_shouldRejectDelivered_whenCodeMissing() {
    // Completing delivery without the customer code is blocked before touching the stop or
    // calling the confirmation path: the producer can't close the delivery alone.
    Order order = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    RouteStop pending = stop(order, RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(pending);

    stubFarmerAuthenticated();
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));

    assertThatThrownBy(
            () ->
                deliveryRouteService.updateStop(
                    route.getId(), pending.getId(), RouteStopStatus.DELIVERED, null, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("obrigatório");

    verify(orderService, never()).confirmDeliveryWithCode(any(), any(), any());
    assertThat(pending.getStatus()).isEqualTo(RouteStopStatus.PENDING);
    assertThat(pending.getCompletedAt()).isNull();
  }

  @Test
  void updateStop_shouldRejectDelivered_whenCodeBlank() {
    Order order = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    RouteStop pending = stop(order, RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(pending);

    stubFarmerAuthenticated();
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));

    assertThatThrownBy(
            () ->
                deliveryRouteService.updateStop(
                    route.getId(), pending.getId(), RouteStopStatus.DELIVERED, "", jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("obrigatório");

    verify(orderService, never()).confirmDeliveryWithCode(any(), any(), any());
    assertThat(pending.getStatus()).isEqualTo(RouteStopStatus.PENDING);
    assertThat(pending.getCompletedAt()).isNull();
  }

  @Test
  void updateStop_shouldConfirmWithCode_whenCodeProvided() {
    Order order = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    RouteStop pending = stop(order, RouteStopStatus.PENDING);
    RouteStop other = stop(order(OrderStatus.IN_DELIVERY, -30.2, -51.2), RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(pending, other);

    stubFarmerAuthenticated();
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    // confirmDeliveryWithCode succeeds (correct code, no lockout).
    when(orderService.confirmDeliveryWithCode(order.getId(), "1234", jwt())).thenReturn(null);

    RouteResponse response =
        deliveryRouteService.updateStop(
            route.getId(), pending.getId(), RouteStopStatus.DELIVERED, "1234", jwt());

    verify(orderService).confirmDeliveryWithCode(order.getId(), "1234", jwt());
    assertThat(pending.getStatus()).isEqualTo(RouteStopStatus.DELIVERED);
    assertThat(pending.getCompletedAt()).isNotNull();
    // Another stop is still PENDING, so the route stays active.
    assertThat(response.getStatus()).isEqualTo(DeliveryRouteStatus.ACTIVE);
  }

  @Test
  void updateStop_shouldPropagateAndNotDeliver_whenCodeWrong() {
    // Wrong code: confirmDeliveryWithCode throws (validation + counter in the hardened path);
    // the exception propagates and the stop does NOT become DELIVERED.
    Order order = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    RouteStop pending = stop(order, RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(pending);

    stubFarmerAuthenticated();
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));
    when(orderService.confirmDeliveryWithCode(order.getId(), "0000", jwt()))
        .thenThrow(new BusinessException("Código de confirmação incorreto"));

    assertThatThrownBy(
            () ->
                deliveryRouteService.updateStop(
                    route.getId(), pending.getId(), RouteStopStatus.DELIVERED, "0000", jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("incorreto");

    assertThat(pending.getStatus()).isEqualTo(RouteStopStatus.PENDING);
    assertThat(pending.getCompletedAt()).isNull();
    verify(deliveryRouteRepository, never()).saveAndFlush(any());
  }

  @Test
  void updateStop_shouldNotTouchOrder_whenStopFails() {
    Order order = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    RouteStop pending = stop(order, RouteStopStatus.PENDING);
    RouteStop other = stop(order(OrderStatus.IN_DELIVERY, -30.2, -51.2), RouteStopStatus.PENDING);
    DeliveryRoute route = activeRouteWithStops(pending, other);

    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RouteResponse response =
        deliveryRouteService.updateStop(
            route.getId(), pending.getId(), RouteStopStatus.FAILED, null, jwt());

    verify(orderService, never()).updateOrderStatus(any(), any(), any());
    verify(orderService, never()).confirmDeliveryWithCode(any(), any(), any());
    // A stop is still PENDING, so the route stays active.
    assertThat(response.getStatus()).isEqualTo(DeliveryRouteStatus.ACTIVE);
  }

  @Test
  void updateStop_shouldRejectInvalidTransition_whenStopAlreadyDelivered() {
    Order order = order(OrderStatus.DELIVERED, -30.1, -51.1);
    RouteStop delivered = stop(order, RouteStopStatus.DELIVERED);
    DeliveryRoute route = activeRouteWithStops(delivered);

    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));

    assertThatThrownBy(
            () ->
                deliveryRouteService.updateStop(
                    route.getId(), delivered.getId(), RouteStopStatus.ARRIVED, null, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Transição de parada inválida");
  }

  @Test
  void updateStop_shouldThrowForbidden_whenRouteBelongsToAnotherFarmer() {
    Producer other = new Producer();
    other.setId(UUID.randomUUID());
    DeliveryRoute route = activeRouteWithStops();
    route.setFarmer(other);

    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));

    assertThatThrownBy(
            () ->
                deliveryRouteService.updateStop(
                    route.getId(), UUID.randomUUID(), RouteStopStatus.ARRIVED, null, jwt()))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void getActiveRoute_shouldThrowNotFound_whenNoneActive() {
    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findByFarmerIdAndStatus(
            farmerUser.getId(), DeliveryRouteStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> deliveryRouteService.getActiveRoute(jwt()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("rota ativa");
  }

  @Test
  void createRoute_shouldGeocodeStop_whenSnapshotHasNoCoordinates() {
    Order noCoords = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    noCoords.getDeliveryAddressSnapshot().setLatitude(null);
    noCoords.getDeliveryAddressSnapshot().setLongitude(null);

    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findByFarmerIdAndStatus(any(), any()))
        .thenReturn(Optional.empty());
    when(orderRepository.findByFarmerIdAndStatusInOrderByCreatedAtAsc(any(), any()))
        .thenReturn(List.of(noCoords));
    when(googleMapsService.geocode(any())).thenReturn(GoogleMapsService.GeocodeOutcome.failed());

    assertThatThrownBy(() -> deliveryRouteService.createRoute(request(), jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Não foi possível localizar o endereço");
    verify(googleRoutesService, never()).computeOptimizedRoundTrip(any(), any());
  }

  @Test
  void createRoute_shouldUseApproximateCoordinates_whenSnapshotEmptyAndGeocodeAmbiguous() {
    // Regression: an address geocoded as AMBIGUOUS (partial_match/APPROXIMATE) still yields
    // usable lat/lng; the route MUST be built, not blocked.
    Order noCoords = order(OrderStatus.IN_DELIVERY, -30.1, -51.1);
    noCoords.getDeliveryAddressSnapshot().setLatitude(null);
    noCoords.getDeliveryAddressSnapshot().setLongitude(null);

    when(userService.requireRole(any(), any(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(farmerUser);
    when(deliveryRouteRepository.findByFarmerIdAndStatus(any(), any()))
        .thenReturn(Optional.empty());
    when(orderRepository.findByFarmerIdAndStatusInOrderByCreatedAtAsc(any(), any()))
        .thenReturn(List.of(noCoords));
    when(googleMapsService.geocode(any()))
        .thenReturn(
            GoogleMapsService.GeocodeOutcome.ambiguous(
                BigDecimal.valueOf(-30.15), BigDecimal.valueOf(-51.15)));
    when(googleRoutesService.computeOptimizedRoundTrip(any(), any()))
        .thenReturn(
            new ComputedRoute(
                BigDecimal.ONE,
                60,
                "poly",
                List.of(0),
                List.of(new RouteLeg(BigDecimal.ONE, 60), new RouteLeg(BigDecimal.ONE, 60))));
    when(googleRoutesService.distancesFromOrigin(any(), any())).thenReturn(null);
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RouteResponse response = deliveryRouteService.createRoute(request(), jwt());

    assertThat(response.getStops()).hasSize(1);
    assertThat(response.getStops().get(0).getLatitude()).isEqualByComparingTo("-30.15");
    verify(googleRoutesService).computeOptimizedRoundTrip(any(), any());
  }

  @Test
  void createRoute_shouldFallBackToHaversineBaseline_whenMatrixFails() {
    Order single = order(OrderStatus.IN_DELIVERY, -30.1, -51.0);
    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findByFarmerIdAndStatus(any(), any()))
        .thenReturn(Optional.empty());
    when(orderRepository.findByFarmerIdAndStatusInOrderByCreatedAtAsc(any(), any()))
        .thenReturn(List.of(single));
    when(googleRoutesService.computeOptimizedRoundTrip(any(), any()))
        .thenReturn(
            new ComputedRoute(
                BigDecimal.TEN,
                600,
                null,
                List.of(0),
                List.of(new RouteLeg(BigDecimal.TEN, 600))));
    when(googleRoutesService.distancesFromOrigin(any(), any())).thenReturn(null);
    when(deliveryRouteRepository.saveAndFlush(any(DeliveryRoute.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RouteResponse response = deliveryRouteService.createRoute(request(), jwt());

    // ~11.1 km haversine (0.1 deg latitude) x 2 — just checks the fallback fired with value > 0.
    assertThat(response.getBaselineDistanceKm()).isGreaterThan(BigDecimal.valueOf(20));
    assertThat(response.getBaselineDistanceKm()).isLessThan(BigDecimal.valueOf(25));
  }

  @Test
  void updateStop_shouldRejectUpdate_whenRouteNotActive() {
    DeliveryRoute route = activeRouteWithStops(stop(order(OrderStatus.DELIVERED, -30.1, -51.1),
        RouteStopStatus.DELIVERED));
    route.setStatus(DeliveryRouteStatus.COMPLETED);

    when(userService.getAuthenticatedUser(any())).thenReturn(farmerUser);
    org.mockito.Mockito.lenient()
        .when(userService.requireRole(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              br.com.ragro.domain.User authenticated =
                  userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType()
                  != inv.<br.com.ragro.domain.enums.TypeUser>getArgument(1)) {
                throw new br.com.ragro.exception.ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
    when(deliveryRouteRepository.findWithStopsById(route.getId())).thenReturn(Optional.of(route));

    assertThatThrownBy(
            () ->
                deliveryRouteService.updateStop(
                    route.getId(), UUID.randomUUID(), RouteStopStatus.ARRIVED, null, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("não está mais ativa");
  }
}
