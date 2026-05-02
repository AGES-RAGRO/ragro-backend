package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import br.com.ragro.controller.response.CustomerOrderResponse;
import br.com.ragro.controller.response.OrderResponse;
import br.com.ragro.domain.*;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
class OrderServiceTest {

  @Mock private UserService userService;
  @Mock private CustomerRepository customerRepository;
  @Mock private CartRepository cartRepository;
  @Mock private CartService cartService;
  @Mock private AddressRepository addressRepository;
  @Mock private PaymentMethodRepository paymentMethodRepository;
  @Mock private StockMovementService stockMovementService;
  @Mock private OrderRepository orderRepository;
  @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;

  @InjectMocks private OrderService orderService;

  private User user;
  private Customer customer;
  private Producer farmer;
  private Product product;
  private Cart cart;
  private CartItem cartItem;
  private Address address;
  private PaymentMethod paymentMethod;

  @BeforeEach
  void setUp() {
    UUID customerId = UUID.randomUUID();
    user = new User();
    user.setId(customerId);
    user.setName("Test Customer");
    user.setType(TypeUser.CUSTOMER);

    customer = new Customer();
    customer.setId(customerId);
    customer.setUser(user);

    farmer = new Producer();
    farmer.setId(UUID.randomUUID());
    farmer.setFarmName("Farm Test");

    product = new Product();
    product.setId(UUID.randomUUID());
    product.setName("Product Test");
    product.setPrice(new BigDecimal("10.00"));
    product.setFarmer(farmer);

    cart = new Cart();
    cart.setId(UUID.randomUUID());
    cart.setCustomer(customer);
    cart.setFarmer(farmer);
    cart.setActive(true);
    cart.setItems(new ArrayList<>());

    cartItem = new CartItem();
    cartItem.setId(UUID.randomUUID());
    cartItem.setProduct(product);
    cartItem.setQuantity(new BigDecimal("2.00"));
    cartItem.setActive(true);
    cart.getItems().add(cartItem);

    address = new Address();
    address.setId(UUID.randomUUID());
    address.setUser(user);
    address.setCity("Test City");
    address.setStreet("Test Street");
    address.setPrimary(true);

    paymentMethod = new PaymentMethod();
    paymentMethod.setId(UUID.randomUUID());
    paymentMethod.setFarmer(farmer);
    paymentMethod.setType("PIX");
    paymentMethod.setActive(true);
  }

  @Test
  void shouldThrowForbidden_whenUserIsNotCustomer() {
    user.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Apenas consumidores podem criar pedidos");
  }

  @Test
  void shouldThrowNotFound_whenCustomerProfileNotFound() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Dados do consumidor não encontrados");
  }

  @Test
  void shouldThrowBusinessException_whenCartIsEmptyOrNotFound() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Carrinho vazio ou não encontrado");
  }

  @Test
  void shouldThrowBusinessException_whenCartHasNoActiveItems() {
    cartItem.setActive(false); // Inactive item
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(cart));

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Seu carrinho não possui itens ativos");
  }

  @Test
  void shouldThrowBusinessException_whenPrimaryAddressNotFound() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(cart));
    when(addressRepository.findByUserIdAndIsPrimaryTrue(customer.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Nenhum endereço principal cadastrado");
  }

  @Test
  void shouldThrowBusinessException_whenFarmerHasNoPaymentMethods() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(cart));
    when(addressRepository.findByUserIdAndIsPrimaryTrue(customer.getId())).thenReturn(Optional.of(address));
    when(paymentMethodRepository.findByFarmerIdAndActiveTrue(farmer.getId())).thenReturn(List.of());

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("O produtor não possui nenhum método de pagamento ativo");
  }

  @Test
  void shouldCreateOrderAndClearCart_whenValidData() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(cart));
    when(addressRepository.findByUserIdAndIsPrimaryTrue(customer.getId())).thenReturn(Optional.of(address));
    when(paymentMethodRepository.findByFarmerIdAndActiveTrue(farmer.getId())).thenReturn(List.of(paymentMethod));

    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
      Order savedOrder = invocation.getArgument(0);
      savedOrder.setId(UUID.randomUUID());
      return savedOrder;
    });

    OrderResponse response = orderService.createOrderFromCart(jwt());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(response.getTotalAmount()).isEqualByComparingTo("20.00"); // 10.00 * 2.00
    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getNotes()).isNull();

    verify(stockMovementService, times(1)).registerSale(eq(product), eq(new BigDecimal("2.00")), anyString());
    verify(cartService, times(1)).clearCart(customer);
    verify(orderRepository, times(1)).saveAndFlush(any(Order.class));
  }

  // ========== Tests for cancelOrder ==========

  @Test
  void shouldCancelOrder_whenStatusIsPending() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.PENDING);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder()
        .street("Test Street").city("Test City").build());
    order.setPaymentMethod(paymentMethod);

    OrderItem orderItem = new OrderItem();
    orderItem.setId(UUID.randomUUID());
    orderItem.setProduct(product);
    orderItem.setProductNameSnapshot("Product Test");
    orderItem.setUnitPriceSnapshot(new BigDecimal("10.00"));
    orderItem.setQuantity(new BigDecimal("2.00"));
    orderItem.setSubtotal(new BigDecimal("20.00"));
    order.getItems().add(orderItem);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.cancelOrder(order.getId(), jwt());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verify(orderRepository).saveAndFlush(any(Order.class));
  }

  @Test
  void shouldThrowForbidden_whenNonCustomerTriesToCancel() {
    user.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(() -> orderService.cancelOrder(UUID.randomUUID(), jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Apenas consumidores podem cancelar pedidos");
  }

  @Test
  void shouldThrowNotFound_whenOrderDoesNotExist() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    UUID fakeId = UUID.randomUUID();
    when(orderRepository.findById(fakeId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.cancelOrder(fakeId, jwt()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Pedido não encontrado");
  }

  @Test
  void shouldThrowForbidden_whenOrderBelongsToAnotherCustomer() {
    Customer otherCustomer = new Customer();
    otherCustomer.setId(UUID.randomUUID());

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(otherCustomer);
    order.setStatus(OrderStatus.PENDING);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Você não tem permissão para cancelar este pedido");
  }

  @Test
  void shouldThrowBusinessException_whenOrderIsNotPending() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setStatus(OrderStatus.CONFIRMED);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Somente pedidos com status PENDING podem ser cancelados");
  }

  @Test
  void shouldUpdateOrderStatus_whenFarmerOwnsOrder() {
    user.setType(TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();
    farmer.setId(user.getId());

    Order order = new Order();
    order.setId(orderId);
    order.setFarmer(farmer);
    order.setCustomer(customer);
    order.setStatus(OrderStatus.PENDING);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.updateOrderStatus(orderId, OrderStatus.IN_DELIVERY, jwt());

    assertThat(response.getStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
    verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
  }

  @Test
  void shouldThrowForbidden_whenNonFarmerUpdatesOrderStatus() {
    user.setType(TypeUser.CUSTOMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(
            () -> orderService.updateOrderStatus(UUID.randomUUID(), OrderStatus.CONFIRMED, jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Apenas produtores podem atualizar o status do pedido");
  }

  @Test
  void shouldThrowNotFound_whenUpdatingOrderStatusAndOrderDoesNotExist() {
    user.setType(TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED, jwt()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Pedido não encontrado");
  }

  @Test
  void shouldThrowForbidden_whenUpdatingOrderStatusAndOrderBelongsToAnotherFarmer() {
    user.setType(TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();

    Producer anotherFarmer = new Producer();
    anotherFarmer.setId(UUID.randomUUID());

    Order order = new Order();
    order.setId(orderId);
    order.setFarmer(anotherFarmer);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED, jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Você não tem permissão para atualizar este pedido");
  }

  @Test
  void shouldReturnOrder_whenCustomerRequestsOwnOrderById() {
    UUID orderId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.PENDING);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);
    farmer.setDisplayPhotoS3("https://cdn.example.com/display.jpg");

    User farmerUser = new User();
    farmerUser.setId(farmer.getId());
    farmerUser.setName("Producer Test");
    farmer.setUser(farmerUser);

    OrderItem orderItem = new OrderItem();
    orderItem.setId(UUID.randomUUID());
    orderItem.setProduct(product);
    orderItem.setProductNameSnapshot("Product Test");
    orderItem.setUnitPriceSnapshot(new BigDecimal("10.00"));
    orderItem.setQuantity(new BigDecimal("2.00"));
    orderItem.setSubtotal(new BigDecimal("20.00"));
    order.getItems().add(orderItem);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(orderRepository.findByIdAndCustomerId(orderId, user.getId())).thenReturn(Optional.of(order));

    CustomerOrderResponse response = orderService.getMyOrderById(orderId, jwt());

    assertThat(response).isNotNull();
    assertThat(response.getPrice()).isEqualByComparingTo("20.00");
    assertThat(response.getProducerName()).isEqualTo("Producer Test");
    assertThat(response.getProducerPicture()).isEqualTo("https://cdn.example.com/display.jpg");
    assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
  }

  @Test
  void shouldThrowForbidden_whenNonCustomerRequestsOrderById() {
    user.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(() -> orderService.getMyOrderById(UUID.randomUUID(), jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Apenas consumidores podem visualizar seus pedidos");
  }

  @Test
  void shouldThrowNotFound_whenOrderByIdDoesNotBelongToCustomer() {
    UUID orderId = UUID.randomUUID();
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(orderRepository.findByIdAndCustomerId(orderId, user.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.getMyOrderById(orderId, jwt()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Pedido não encontrado para este consumidor");
  }

  private Jwt jwt() {
    return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300),
        Map.of("alg", "none"), Map.of("sub", "sub"));
  }
}
