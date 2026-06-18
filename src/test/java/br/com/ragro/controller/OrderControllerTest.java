package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.ActiveUserFilter;
import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.controller.response.CustomerOrderResponse;
import br.com.ragro.controller.response.OrderResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class, ActiveUserFilter.class})
class OrderControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private OrderService orderService;
  @MockBean private UserRepository userRepository;

  // ─── POST /orders ──────────────────────────────────────────────────────

  @Test
  void createOrder_shouldReturn201WithOrder() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.CUSTOMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.createOrderFromCart(any())).thenReturn(orderResponse(orderId));

    mockMvc
        .perform(post("/orders").with(customerJwt(sub)).with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(orderId.toString()))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  // ─── GET /orders/consumer ──────────────────────────────────────────────

  @Test
  void getMyOrders_shouldReturn200WithList() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.CUSTOMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.getMyOrders(any())).thenReturn(List.of(customerOrderResponse(orderId)));

    mockMvc
        .perform(get("/orders/consumer").with(customerJwt(sub)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(orderId.toString()));
  }

  @Test
  void getMyOrders_shouldReturn200WithEmptyList_whenNoOrders() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.CUSTOMER);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.getMyOrders(any())).thenReturn(List.of());

    mockMvc
        .perform(get("/orders/consumer").with(customerJwt(sub)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // ─── GET /orders/producer ──────────────────────────────────────────────

  @Test
  void getProducerOrders_shouldReturn200WithList() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.getProducerOrders(any())).thenReturn(List.of(orderResponse(orderId)));

    mockMvc
        .perform(get("/orders/producer").with(farmerJwt(sub)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(orderId.toString()));
  }

  // ─── GET /orders/customer/{id} ─────────────────────────────────────────

  @Test
  void getMyOrderById_shouldReturn200WithOrder() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.CUSTOMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.getMyOrderById(eq(orderId), any()))
        .thenReturn(customerOrderResponse(orderId));

    mockMvc
        .perform(get("/orders/customer/{id}", orderId).with(customerJwt(sub)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(orderId.toString()))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  // ─── PATCH /orders/{id}/status ─────────────────────────────────────────

  @Test
  void updateOrderStatus_shouldReturn200WithUpdatedOrder() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.updateOrderStatus(eq(orderId), eq(OrderStatus.CONFIRMED), any()))
        .thenReturn(orderResponseWithStatus(orderId, OrderStatus.CONFIRMED));

    mockMvc
        .perform(
            patch("/orders/{id}/status", orderId)
                .with(farmerJwt(sub))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"CONFIRMED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    verify(orderService).updateOrderStatus(eq(orderId), eq(OrderStatus.CONFIRMED), any());
  }

  @Test
  void updateOrderStatus_shouldReturn400_whenStatusIsMissing() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            patch("/orders/{id}/status", orderId)
                .with(farmerJwt(sub))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  // ─── PATCH /orders/{id}/confirm ────────────────────────────────────────

  @Test
  void confirmOrder_shouldReturn200WithConfirmedOrder() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.confirmOrder(eq(orderId), any()))
        .thenReturn(orderResponseWithStatus(orderId, OrderStatus.CONFIRMED));

    mockMvc
        .perform(patch("/orders/{id}/confirm", orderId).with(farmerJwt(sub)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  // ─── PATCH /orders/{id}/cancel ─────────────────────────────────────────

  @Test
  void cancelOrder_shouldReturn200WithCancelledOrder_whenBodyIsProvided() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.CUSTOMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.cancelOrder(eq(orderId), any(), any()))
        .thenReturn(orderResponseWithStatus(orderId, OrderStatus.CANCELLED));

    mockMvc
        .perform(
            patch("/orders/{id}/cancel", orderId)
                .with(customerJwt(sub))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Mudei de ideia\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void cancelOrder_shouldReturn200_whenNoBodyIsProvided() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.CUSTOMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.cancelOrder(eq(orderId), any(), eq(null)))
        .thenReturn(orderResponseWithStatus(orderId, OrderStatus.CANCELLED));

    mockMvc
        .perform(patch("/orders/{id}/cancel", orderId).with(customerJwt(sub)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void cancelOrder_shouldReturn400_whenReasonExceedsMaxLength() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.CUSTOMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    String tooLong = "a".repeat(101);

    mockMvc
        .perform(
            patch("/orders/{id}/cancel", orderId)
                .with(customerJwt(sub))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("reason", tooLong))))
        .andExpect(status().isBadRequest());
  }

  // ─── PATCH /orders/customer/{id}/cancel ────────────────────────────────

  @Test
  void cancelOrderAsCustomer_shouldReturn200WithCancelledOrder() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.CUSTOMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.cancelOrderAsCustomer(eq(orderId), any(), any()))
        .thenReturn(orderResponseWithStatus(orderId, OrderStatus.CANCELLED));

    mockMvc
        .perform(
            patch("/orders/customer/{id}/cancel", orderId).with(customerJwt(sub)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    verify(orderService).cancelOrderAsCustomer(eq(orderId), any(), any());
  }

  // ─── PATCH /orders/customer/{id}/confirm-delivery ──────────────────────

  @Test
  void confirmDelivery_shouldReturn200WithDeliveredOrder() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.CUSTOMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.confirmDelivery(eq(orderId), any()))
        .thenReturn(orderResponseWithStatus(orderId, OrderStatus.DELIVERED));

    mockMvc
        .perform(
            patch("/orders/customer/{id}/confirm-delivery", orderId)
                .with(customerJwt(sub))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"));
  }

  // ─── PATCH /orders/{id}/refuse ──────────────────────────────────────────

  @Test
  void refuseOrder_shouldReturn200WithCancelledOrder() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.refuseOrderAsFarmer(eq(orderId), any(), any()))
        .thenReturn(orderResponseWithStatus(orderId, OrderStatus.CANCELLED));

    mockMvc
        .perform(patch("/orders/{id}/refuse", orderId).with(farmerJwt(sub)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    verify(orderService).refuseOrderAsFarmer(eq(orderId), any(), any());
  }

  // ─── PATCH /orders/{id}/seen ────────────────────────────────────────────

  @Test
  void markAsSeen_shouldReturn200WithOrder() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.markOrderAsSeen(eq(orderId), any())).thenReturn(orderResponse(orderId));

    mockMvc
        .perform(patch("/orders/{id}/seen", orderId).with(farmerJwt(sub)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(orderId.toString()));

    verify(orderService).markOrderAsSeen(eq(orderId), any());
  }

  // ─── POST /orders/{id}/repeat ───────────────────────────────────────────

  @Test
  void repeatOrder_shouldReturn201WithCart() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub, br.com.ragro.domain.enums.TypeUser.CUSTOMER);
    UUID orderId = UUID.randomUUID();
    UUID farmerId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(orderService.repeatOrder(eq(orderId), any()))
        .thenReturn(cartResponse(farmerId));

    mockMvc
        .perform(post("/orders/{id}/repeat", orderId).with(customerJwt(sub)).with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.farmerId").value(farmerId.toString()));

    verify(orderService).repeatOrder(eq(orderId), any());
  }

  // ─── role guards ───────

  @Test
  void createOrder_shouldReturn401_whenNoJwtProvided() throws Exception {
    mockMvc.perform(post("/orders").with(csrf())).andExpect(status().isUnauthorized());
  }

  // ─── helpers ─────────────────────────────────────────────────────────

  private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customerJwt(String sub) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
  }

  private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor farmerJwt(String sub) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"));
  }

  private OrderResponse orderResponse(UUID id) {
    return orderResponseWithStatus(id, OrderStatus.PENDING);
  }

  private OrderResponse orderResponseWithStatus(UUID id, OrderStatus status) {
    return OrderResponse.builder()
        .id(id)
        .customerId(UUID.randomUUID())
        .customerName("Maria Silva")
        .farmerId(UUID.randomUUID())
        .farmerName("Fazenda Boa Vista")
        .status(status)
        .paymentStatus(br.com.ragro.domain.enums.PaymentStatus.PENDING)
        .totalAmount(new BigDecimal("50.00"))
        .isNew(true)
        .createdAt(OffsetDateTime.now())
        .items(List.of())
        .build();
  }

  private CustomerOrderResponse customerOrderResponse(UUID id) {
    return CustomerOrderResponse.builder()
        .id(id)
        .price(new BigDecimal("50.00"))
        .producerName("Fazenda Boa Vista")
        .status(OrderStatus.PENDING)
        .reviewed(false)
        .build();
  }

  private CartResponse cartResponse(UUID farmerId) {
    return CartResponse.builder()
        .id(UUID.randomUUID())
        .farmerId(farmerId)
        .farmName("Fazenda Boa Vista")
        .items(List.of())
        .totalAmount(new BigDecimal("30.00"))
        .build();
  }

 private User buildUser(String authSub, br.com.ragro.domain.enums.TypeUser type) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test User");
    user.setEmail("user@test.com");
    user.setType(type);
    user.setActive(true);
    user.setAuthSub(authSub);
    return user;
  }
}