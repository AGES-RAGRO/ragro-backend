package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.CancelOrderRequest;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.controller.response.CustomerOrderResponse;
import br.com.ragro.controller.response.OrderResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.AddressSnapshot;
import br.com.ragro.domain.Cart;
import br.com.ragro.domain.CartItem;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.OrderItem;
import br.com.ragro.domain.OrderStatusHistory;
import br.com.ragro.domain.PaymentMethod;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.PaymentStatus;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.CartRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.OrderStatusHistoryRepository;
import br.com.ragro.repository.PaymentMethodRepository;
import br.com.ragro.repository.ReviewRepository;
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
  @Mock private ReviewRepository reviewRepository;
  @Mock private MinioStorageService storageService;
  @Mock private NotificationService notificationService;

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

    // Mapper resolves URLs via MinioStorageService; return the raw key to keep assertions simple.
    lenient()
        .when(storageService.composePublicUrl(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
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
        .hasMessageContaining("Dados do consumidor");
  }

  @Test
  void shouldThrowBusinessException_whenCartIsEmptyOrNotFound() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Carrinho vazio");
  }

  @Test
  void shouldThrowBusinessException_whenCartHasNoActiveItems() {
    cartItem.setActive(false);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId()))
        .thenReturn(Optional.of(cart));

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("itens ativos");
  }

  @Test
  void shouldThrowBusinessException_whenPrimaryAddressNotFound() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId()))
        .thenReturn(Optional.of(cart));
    when(addressRepository.findByUserIdAndIsPrimaryTrue(customer.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("principal cadastrado");
  }

  @Test
  void shouldThrowBusinessException_whenFarmerHasNoPaymentMethods() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId()))
        .thenReturn(Optional.of(cart));
    when(addressRepository.findByUserIdAndIsPrimaryTrue(customer.getId()))
        .thenReturn(Optional.of(address));
    when(paymentMethodRepository.findByFarmerIdAndActiveTrueOrderByCreatedAtAsc(farmer.getId()))
        .thenReturn(List.of());

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("pagamento ativo");
  }

  @Test
  void shouldCreateOrderAndClearCart_whenValidData() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId()))
        .thenReturn(Optional.of(cart));
    when(addressRepository.findByUserIdAndIsPrimaryTrue(customer.getId()))
        .thenReturn(Optional.of(address));
    when(paymentMethodRepository.findByFarmerIdAndActiveTrueOrderByCreatedAtAsc(farmer.getId()))
        .thenReturn(List.of(paymentMethod));
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    OrderResponse response = orderService.createOrderFromCart(jwt());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    verify(cartService, times(1)).clearCart(customer);
    verify(orderRepository, times(1)).saveAndFlush(any(Order.class));
  }

  @Test
  void shouldCancelOrder_whenStatusIsPending() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.PENDING);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);
    OrderItem orderItem = new OrderItem();
    orderItem.setProduct(product);
    orderItem.setProductNameSnapshot("Product Test");
    orderItem.setUnitPriceSnapshot(new BigDecimal("10.00"));
    orderItem.setQuantity(new BigDecimal("2.00"));
    orderItem.setSubtotal(new BigDecimal("20.00"));
    order.getItems().add(orderItem);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.cancelOrder(order.getId(), jwt(), null);
    assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    // D26: cancelling a PENDING order must NOT touch stock — PENDING never debited it.
    verify(stockMovementService, never()).registerCancelledSale(any(), any(), anyString());
  }

  @Test
  void shouldThrowForbidden_whenNonCustomerTriesToCancel() {
    user.setType(TypeUser.ADMIN);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(() -> orderService.cancelOrder(UUID.randomUUID(), jwt(), null))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldThrowNotFound_whenOrderDoesNotExist() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    UUID fakeId = UUID.randomUUID();
    when(orderRepository.findById(fakeId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.cancelOrder(fakeId, jwt(), null))
        .isInstanceOf(NotFoundException.class);
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

    assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), jwt(), null))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldThrowBusinessException_whenOrderIsNotCancellable() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setStatus(OrderStatus.DELIVERED);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), jwt(), null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void shouldCancelOrder_whenProducerCancelsOwnedPendingOrder() {
    user.setType(TypeUser.FARMER);
    farmer.setId(user.getId());

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.PENDING);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.cancelOrder(order.getId(), jwt(), null);

    assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getCancellationReason()).isEqualTo("REFUSED_BY_FARMER");
  }

  @Test
  void shouldThrowForbidden_whenProducerCancelsOrderFromAnotherProducer() {
    user.setType(TypeUser.FARMER);

    Producer anotherFarmer = new Producer();
    anotherFarmer.setId(UUID.randomUUID());

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(anotherFarmer);
    order.setStatus(OrderStatus.PENDING);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), jwt(), null))
        .isInstanceOf(ForbiddenException.class);
  }

  // ---------- cancelOrderAsCustomer ----------

  @Test
  void shouldCancelOrderAsCustomer_andPersistReasonAndDetails() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.PENDING);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);

    CancelOrderRequest request = new CancelOrderRequest();
    request.setReason("CHANGED_MY_MIND");
    request.setDetails("Comprei em outro lugar");

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.cancelOrderAsCustomer(order.getId(), jwt(), request);

    assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getCancellationReason()).isEqualTo("CHANGED_MY_MIND");
    assertThat(order.getCancellationDetails()).isEqualTo("Comprei em outro lugar");
    // OrderResponse must expose the reason/details (consumed by the mobile cancellation card).
    assertThat(response.getCancellationReason()).isEqualTo("CHANGED_MY_MIND");
    assertThat(response.getCancellationDetails()).isEqualTo("Comprei em outro lugar");
    verify(stockMovementService, never()).registerCancelledSale(any(), any(), anyString());
  }

  @Test
  void shouldUseDefaultReason_whenCustomerCancelsWithoutBody() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.PENDING);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    orderService.cancelOrderAsCustomer(order.getId(), jwt(), null);

    assertThat(order.getCancellationReason()).isEqualTo("CUSTOMER_CANCELLED");
    assertThat(order.getCancellationDetails()).isNull();
  }

  @Test
  void shouldCancelOrderAsCustomer_whenStatusIsConfirmed() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.CONFIRMED);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);
    OrderItem orderItem = new OrderItem();
    orderItem.setProduct(product);
    orderItem.setProductNameSnapshot("Product Test");
    orderItem.setUnitPriceSnapshot(new BigDecimal("10.00"));
    orderItem.setQuantity(new BigDecimal("2.00"));
    orderItem.setSubtotal(new BigDecimal("20.00"));
    order.getItems().add(orderItem);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.cancelOrderAsCustomer(order.getId(), jwt(), null);

    assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    // Cancelling a CONFIRMED order must credit the stock that confirmation debited.
    verify(stockMovementService)
        .registerCancelledSale(eq(product), eq(new BigDecimal("2.00")), anyString());
  }

  @Test
  void shouldThrowForbidden_whenNonCustomerCallsCustomerCancel() {
    user.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(() -> orderService.cancelOrderAsCustomer(UUID.randomUUID(), jwt(), null))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("consumidores");
  }

  @Test
  void shouldThrowForbidden_whenCustomerCancelsOrderFromAnotherCustomer() {
    Customer otherCustomer = new Customer();
    otherCustomer.setId(UUID.randomUUID());
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(otherCustomer);
    order.setStatus(OrderStatus.PENDING);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.cancelOrderAsCustomer(order.getId(), jwt(), null))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldThrowBusinessException_whenCustomerCancelsNonCancellableOrder() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setStatus(OrderStatus.DELIVERED);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.cancelOrderAsCustomer(order.getId(), jwt(), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("PENDING, CONFIRMED ou IN_DELIVERY");
  }

  // ---------- refuseOrderAsFarmer ----------

  @Test
  void shouldRefuseOrderAsFarmer_andTagReason() {
    user.setType(TypeUser.FARMER);
    farmer.setId(user.getId());

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.PENDING);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.refuseOrderAsFarmer(order.getId(), jwt(), null);

    assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getCancellationReason()).isEqualTo("REFUSED_BY_FARMER");
    verify(stockMovementService, never()).registerCancelledSale(any(), any(), anyString());
    verify(notificationService).createCustomerOrderRefusedNotification(order);
  }

  @Test
  void shouldRefuseOrderAsFarmer_whenStatusIsInDelivery() {
    user.setType(TypeUser.FARMER);
    farmer.setId(user.getId());

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.IN_DELIVERY);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);
    OrderItem orderItem = new OrderItem();
    orderItem.setProduct(product);
    orderItem.setProductNameSnapshot("Product Test");
    orderItem.setUnitPriceSnapshot(new BigDecimal("10.00"));
    orderItem.setQuantity(new BigDecimal("2.00"));
    orderItem.setSubtotal(new BigDecimal("20.00"));
    order.getItems().add(orderItem);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.refuseOrderAsFarmer(order.getId(), jwt(), null);

    assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    // Refusing an IN_DELIVERY order must credit the stock that confirmation debited.
    verify(stockMovementService)
        .registerCancelledSale(eq(product), eq(new BigDecimal("2.00")), anyString());
  }

  @Test
  void shouldThrowForbidden_whenNonFarmerRefuses() {
    user.setType(TypeUser.CUSTOMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(() -> orderService.refuseOrderAsFarmer(UUID.randomUUID(), jwt(), null))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("produtores");
  }

  @Test
  void shouldThrowForbidden_whenFarmerRefusesOrderFromAnotherProducer() {
    user.setType(TypeUser.FARMER);

    Producer anotherFarmer = new Producer();
    anotherFarmer.setId(UUID.randomUUID());

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(anotherFarmer);
    order.setStatus(OrderStatus.PENDING);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.refuseOrderAsFarmer(order.getId(), jwt(), null))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldThrowBusinessException_whenFarmerRefusesNonCancellableOrder() {
    user.setType(TypeUser.FARMER);
    farmer.setId(user.getId());

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.DELIVERED);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.refuseOrderAsFarmer(order.getId(), jwt(), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("PENDING, CONFIRMED ou IN_DELIVERY");
  }

  // ---------- confirmDelivery ----------

  @Test
  void shouldConfirmDelivery_whenCustomerOwnsInDeliveryOrder() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.IN_DELIVERY);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.confirmDelivery(order.getId(), jwt());

    assertThat(response.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    assertThat(order.getStatusHistory()).hasSize(1);
    assertThat(order.getStatusHistory().get(0).getStatus()).isEqualTo(OrderStatus.DELIVERED);
    verify(notificationService).createCustomerOrderDeliveredNotification(order);
  }

  @Test
  void shouldThrowForbidden_whenNonCustomerConfirmsDelivery() {
    user.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(() -> orderService.confirmDelivery(UUID.randomUUID(), jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("consumidores");
  }

  @Test
  void shouldThrowForbidden_whenCustomerConfirmsDeliveryOfAnotherCustomer() {
    Customer otherCustomer = new Customer();
    otherCustomer.setId(UUID.randomUUID());
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(otherCustomer);
    order.setStatus(OrderStatus.IN_DELIVERY);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.confirmDelivery(order.getId(), jwt()))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldThrowBusinessException_whenConfirmingDeliveryOfNonInDeliveryOrder() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setStatus(OrderStatus.PENDING);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.confirmDelivery(order.getId(), jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("entrega");
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

    OrderResponse response =
        orderService.updateOrderStatus(orderId, OrderStatus.IN_DELIVERY, jwt());

    assertThat(response.getStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
    verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
    verify(notificationService).createCustomerOrderInDeliveryNotification(order);
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
        .hasMessageContaining("Pedido");
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
        .hasMessageContaining("permiss");
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
    farmer.setAvatarS3("https://cdn.example.com/avatar.jpg");
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
    order.setCancellationReason("CUSTOMER_CANCELLED");
    order.setCancellationDetails("mudei de ideia");

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(orderRepository.findByIdAndCustomerId(orderId, user.getId()))
        .thenReturn(Optional.of(order));
    when(reviewRepository.existsByOrderId(orderId)).thenReturn(true);

    CustomerOrderResponse response = orderService.getMyOrderById(orderId, jwt());

    assertThat(response).isNotNull();
    assertThat(response.getPrice()).isEqualByComparingTo("20.00");
    assertThat(response.getProducerName()).isEqualTo("Producer Test");
    assertThat(response.getProducerPicture()).isEqualTo("https://cdn.example.com/avatar.jpg");
    assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(response.isReviewed()).isTrue();
    // The customer detail must expose the cancellation reason/details.
    assertThat(response.getCancellationReason()).isEqualTo("CUSTOMER_CANCELLED");
    assertThat(response.getCancellationDetails()).isEqualTo("mudei de ideia");
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
        .hasMessageContaining("consumidor");
  }

  @Test
  void shouldConfirmOrderAndRegisterStockOutput_whenFarmerOwnsPendingOrder() {
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

    OrderItem orderItem = new OrderItem();
    orderItem.setProduct(product);
    orderItem.setQuantity(new BigDecimal("2.00"));
    orderItem.setSubtotal(new BigDecimal("20.00"));
    order.getItems().add(orderItem);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.confirmOrder(orderId, jwt());

    assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(stockMovementService).registerSale(eq(product), eq(new BigDecimal("2.00")), anyString());
    verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
    verify(notificationService).createCustomerOrderAcceptedNotification(order);
  }

  @Test
  void shouldThrowForbidden_whenNonFarmerConfirmsOrder() {
    user.setType(TypeUser.CUSTOMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(() -> orderService.confirmOrder(UUID.randomUUID(), jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Apenas produtores podem confirmar pedidos");
  }

  @Test
  void shouldThrowForbidden_whenFarmerConfirmsOrderFromAnotherProducer() {
    user.setType(TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();

    Producer anotherFarmer = new Producer();
    anotherFarmer.setId(UUID.randomUUID());

    Order order = new Order();
    order.setId(orderId);
    order.setFarmer(anotherFarmer);
    order.setStatus(OrderStatus.PENDING);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.confirmOrder(orderId, jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("permiss");
  }

  @Test
  void shouldThrowBusinessException_whenConfirmingOrderThatIsNotPending() {
    user.setType(TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();
    farmer.setId(user.getId());

    Order order = new Order();
    order.setId(orderId);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.CANCELLED);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.confirmOrder(orderId, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Somente pedidos com status PENDING podem ser confirmados");
  }

  @Test
  void shouldThrowForbidden_whenNonCustomerTriesToRepeatOrder() {
    user.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(() -> orderService.repeatOrder(UUID.randomUUID(), jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Apenas consumidores podem repetir pedidos");
  }

  @Test
  void shouldRepeatOrder_andCreateNewCart_whenNoActiveCart() {
    UUID orderId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setCustomer(customer);
    order.setFarmer(farmer);

    OrderItem orderItem = new OrderItem();
    orderItem.setId(UUID.randomUUID());
    orderItem.setProduct(product);
    product.setStockQuantity(new BigDecimal("10.00"));
    product.setActive(true);
    orderItem.setQuantity(new BigDecimal("2.00"));
    order.setItems(new ArrayList<>(List.of(orderItem)));

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId()))
        .thenReturn(Optional.empty());
    when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

    CartResponse response = orderService.repeatOrder(orderId, jwt());

    assertThat(response).isNotNull();
    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getItems().get(0).getQuantity()).isEqualByComparingTo("2.00");
    verify(cartRepository, times(1)).saveAndFlush(any(Cart.class));
  }

  @Test
  void shouldClearCart_whenRepeatingOrderFromDifferentFarmer() {
    UUID orderId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setCustomer(customer);

    Producer differentFarmer = new Producer();
    differentFarmer.setId(UUID.randomUUID());
    order.setFarmer(differentFarmer);

    Product differentProduct = new Product();
    differentProduct.setId(UUID.randomUUID());
    differentProduct.setFarmer(differentFarmer);
    differentProduct.setStockQuantity(new BigDecimal("10.00"));
    differentProduct.setActive(true);
    differentProduct.setPrice(new BigDecimal("5.00"));
    differentProduct.setName("Different Product");
    differentProduct.setUnityType("unit");

    OrderItem orderItem = new OrderItem();
    orderItem.setId(UUID.randomUUID());
    orderItem.setProduct(differentProduct);
    orderItem.setQuantity(new BigDecimal("2.00"));
    order.setItems(new ArrayList<>(List.of(orderItem)));

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId()))
        .thenReturn(Optional.of(cart));
    when(cartRepository.saveAndFlush(any(Cart.class)))
        .thenAnswer(
            inv -> {
              Cart savedCart = inv.getArgument(0);
              savedCart.setId(UUID.randomUUID());
              return savedCart;
            });

    CartResponse response = orderService.repeatOrder(orderId, jwt());

    verify(cartService, times(1)).clearCart(customer);
    assertThat(response).isNotNull();
    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getItems().get(0).getQuantity()).isEqualByComparingTo("2.00");
  }

  @Test
  void shouldCapQuantity_whenRequestedExceedsStock() {
    UUID orderId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setCustomer(customer);
    order.setFarmer(farmer);

    OrderItem orderItem = new OrderItem();
    orderItem.setId(UUID.randomUUID());
    orderItem.setProduct(product);
    product.setStockQuantity(new BigDecimal("1.00"));
    product.setActive(true);
    orderItem.setQuantity(new BigDecimal("5.00"));
    order.setItems(new ArrayList<>(List.of(orderItem)));

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId()))
        .thenReturn(Optional.empty());
    when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

    CartResponse response = orderService.repeatOrder(orderId, jwt());

    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getItems().get(0).getQuantity()).isEqualByComparingTo("1.00");
  }

  @Test
  void shouldThrowBusinessException_whenNoItemsAvailableInStock() {
    UUID orderId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setCustomer(customer);
    order.setFarmer(farmer);

    OrderItem orderItem = new OrderItem();
    orderItem.setId(UUID.randomUUID());
    orderItem.setProduct(product);
    product.setStockQuantity(new BigDecimal("0.00"));
    product.setActive(true);
    orderItem.setQuantity(new BigDecimal("2.00"));
    order.setItems(new ArrayList<>(List.of(orderItem)));

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.repeatOrder(orderId, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("dispon");
  }

  private Jwt jwt() {
    return new Jwt(
        "token",
        Instant.now(),
        Instant.now().plusSeconds(300),
        Map.of("alg", "none"),
        Map.of("sub", "sub"));
  }

  @Test
  void createOrderFromCart_shouldPersistOrderWithSnapshottedItemValues() {
    UUID customerId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);
    Producer farmer = buildFarmer(UUID.randomUUID());
    Address address = buildAddress();
 
    Product product = buildProduct(new BigDecimal("5.00"), new BigDecimal("100"));
    CartItem cartItem = new CartItem();
    cartItem.setProduct(product);
    cartItem.setQuantity(new BigDecimal("3"));
    cartItem.setActive(true);
 
    Cart cart = new Cart();
    cart.setFarmer(farmer);
    cart.getItems().add(cartItem);
 
    PaymentMethod paymentMethod = new PaymentMethod();
    paymentMethod.setId(UUID.randomUUID());
    paymentMethod.setFarmer(farmer);
 
    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customerId)).thenReturn(Optional.of(cart));
    when(addressRepository.findByUserIdAndIsPrimaryTrue(customerId))
        .thenReturn(Optional.of(address));
    when(paymentMethodRepository.findByFarmerIdAndActiveTrueOrderByCreatedAtAsc(farmer.getId()))
        .thenReturn(List.of(paymentMethod));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
 
    OrderResponse response = orderService.createOrderFromCart(jwt);
 
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    verify(cartService).clearCart(customer);
 
    org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).saveAndFlush(captor.capture());
    Order savedOrder = captor.getValue();
 
    assertThat(savedOrder.getDeliveryAddress()).isEqualTo(address);
    assertThat(savedOrder.getDeliveryAddressSnapshot()).isNotNull();
    assertThat(savedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(savedOrder.getNotes()).isNull();
    assertThat(savedOrder.getItems()).hasSize(1);
    OrderItem item = savedOrder.getItems().get(0);
    assertThat(item.getProductNameSnapshot()).isEqualTo("Tomate");
    assertThat(item.getUnitPriceSnapshot()).isEqualByComparingTo("5.00");
    assertThat(item.getUnityTypeSnapshot()).isEqualTo("kg");
    assertThat(item.getQuantity()).isEqualByComparingTo("3");
    assertThat(item.getSubtotal()).isEqualByComparingTo("15.00");
    assertThat(savedOrder.getStatusHistory()).hasSize(1);
    assertThat(savedOrder.getStatusHistory().get(0).getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(savedOrder.getStatusHistory().get(0).getOrder()).isEqualTo(savedOrder);
  }
 
  @Test
  void createOrderFromCart_shouldThrow_whenUserIsNotCustomer() {
    User user = buildUser(UUID.randomUUID(), TypeUser.FARMER);
    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
 
    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt))
        .isInstanceOf(ForbiddenException.class);
  }
 
  @Test
  void createOrderFromCart_shouldThrow_whenCartHasNoActiveItems() {
    UUID customerId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);
 
    CartItem inactiveItem = new CartItem();
    inactiveItem.setActive(false);
    Cart cart = new Cart();
    cart.getItems().add(inactiveItem);
 
    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customerId)).thenReturn(Optional.of(cart));
 
    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("itens ativos");
  }
 
  // ─── getMyOrders ─────────────────────────────────────────────────────────
 
@Test
  void getMyOrders_shouldReturnMappedList_whenCustomerHasOrders() {
    UUID customerId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);
    Order order = buildOrder(UUID.randomUUID(), customer, buildFarmer(UUID.randomUUID()), OrderStatus.PENDING);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
        .thenReturn(List.of(order));
    when(reviewRepository.existsByOrderId(order.getId())).thenReturn(true);

    List<CustomerOrderResponse> result = orderService.getMyOrders(jwt);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).isReviewed()).isTrue();
  }

  @Test
  void getMyOrders_shouldReturnEmptyList_whenNoOrders() {
    UUID customerId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)).thenReturn(List.of());

    List<CustomerOrderResponse> result = orderService.getMyOrders(jwt);

    assertThat(result).isEmpty();
  }

  @Test
  void getMyOrders_shouldThrow_whenUserIsNotCustomer() {
    User user = buildUser(UUID.randomUUID(), TypeUser.FARMER);
    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

    assertThatThrownBy(() -> orderService.getMyOrders(jwt)).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void getMyOrders_shouldThrow_whenCustomerDataNotFound() {
    UUID customerId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.getMyOrders(jwt)).isInstanceOf(NotFoundException.class);
  }

  // ─── getProducerOrders ───────────────────────────────────────────────────

  @Test
  void getProducerOrders_shouldReturnMappedList() {
    UUID farmerId = UUID.randomUUID();
    User user = buildUser(farmerId, TypeUser.FARMER);
    Order order = buildOrder(UUID.randomUUID(), buildCustomer(UUID.randomUUID()), buildFarmer(farmerId), OrderStatus.PENDING);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findByFarmerIdOrderByCreatedAtDesc(farmerId)).thenReturn(List.of(order));

    List<OrderResponse> result = orderService.getProducerOrders(jwt);

    assertThat(result).hasSize(1);
  }

  @Test
  void getProducerOrders_shouldThrow_whenUserIsNotFarmer() {
    User user = buildUser(UUID.randomUUID(), TypeUser.CUSTOMER);
    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

    assertThatThrownBy(() -> orderService.getProducerOrders(jwt))
        .isInstanceOf(ForbiddenException.class);
  }

  // ─── getMyOrderById ──────────────────────────────────────────────────────

  @Test
  void getMyOrderById_shouldReturnOrder_whenItBelongsToCustomer() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);
    Order order = buildOrder(orderId, customer, buildFarmer(UUID.randomUUID()), OrderStatus.PENDING);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
    when(reviewRepository.existsByOrderId(orderId)).thenReturn(false);

    CustomerOrderResponse result = orderService.getMyOrderById(orderId, jwt);

    assertThat(result.getId()).isEqualTo(orderId);
    assertThat(result.isReviewed()).isFalse();
  }

  @Test
  void getMyOrderById_shouldThrow_whenOrderNotFoundForCustomer() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.getMyOrderById(orderId, jwt))
        .isInstanceOf(NotFoundException.class);
  }

  // ─── markOrderAsSeen ─────────────────────────────────────────────────────

  @Test
  void markOrderAsSeen_shouldSetSeenAndSave_whenNotYetSeen() {
    UUID farmerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(farmerId, TypeUser.FARMER);
    Producer farmer = buildFarmer(farmerId);
    Order order = buildOrder(orderId, buildCustomer(UUID.randomUUID()), farmer, OrderStatus.PENDING);
    order.setSeenByFarmer(false);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(order)).thenReturn(order);

    OrderResponse response = orderService.markOrderAsSeen(orderId, jwt);

    assertThat(response).isNotNull();
    assertThat(order.isSeenByFarmer()).isTrue();
    verify(orderRepository).saveAndFlush(order);
  }

  @Test
  void markOrderAsSeen_shouldNotSave_whenAlreadySeen() {
    UUID farmerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(farmerId, TypeUser.FARMER);
    Producer farmer = buildFarmer(farmerId);
    Order order = buildOrder(orderId, buildCustomer(UUID.randomUUID()), farmer, OrderStatus.PENDING);
    order.setSeenByFarmer(true);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    orderService.markOrderAsSeen(orderId, jwt);

    verify(orderRepository, never()).saveAndFlush(any(Order.class));
  }

  @Test
  void markOrderAsSeen_shouldThrow_whenUserIsNotFarmer() {
    User user = buildUser(UUID.randomUUID(), TypeUser.CUSTOMER);
    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

    assertThatThrownBy(() -> orderService.markOrderAsSeen(UUID.randomUUID(), jwt))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void markOrderAsSeen_shouldThrow_whenFarmerDoesNotOwnOrder() {
    UUID farmerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(farmerId, TypeUser.FARMER);
    Producer otherFarmer = buildFarmer(UUID.randomUUID());
    Order order = buildOrder(orderId, buildCustomer(UUID.randomUUID()), otherFarmer, OrderStatus.PENDING);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.markOrderAsSeen(orderId, jwt))
        .isInstanceOf(ForbiddenException.class);
  }

  // ─── confirmOrder ────────────────────────────────────────────────────────

  @Test
  void confirmOrder_shouldRegisterSaleAndRecordHistory() {
    UUID farmerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(farmerId, TypeUser.FARMER);
    Producer farmer = buildFarmer(farmerId);
    Order order = buildOrder(orderId, buildCustomer(UUID.randomUUID()), farmer, OrderStatus.PENDING);

    Product product = buildProduct(new BigDecimal("5.00"), new BigDecimal("10"));
    OrderItem item = new OrderItem();
    item.setProduct(product);
    item.setQuantity(new BigDecimal("2"));
    item.setSubtotal(new BigDecimal("10.00"));
    order.getItems().add(item);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(order)).thenReturn(order);

    OrderResponse response = orderService.confirmOrder(orderId, jwt);

    assertThat(response).isNotNull();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(stockMovementService).registerSale(eq(product), eq(new BigDecimal("2")), any());

    org.mockito.ArgumentCaptor<br.com.ragro.domain.OrderStatusHistory> historyCaptor =
        org.mockito.ArgumentCaptor.forClass(br.com.ragro.domain.OrderStatusHistory.class);
    verify(orderStatusHistoryRepository).save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getOrder()).isEqualTo(order);
    assertThat(historyCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

    verify(notificationService).createCustomerOrderAcceptedNotification(order);
  }

  @Test
  void confirmOrder_shouldThrow_whenOrderIsNotPending() {
    UUID farmerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(farmerId, TypeUser.FARMER);
    Producer farmer = buildFarmer(farmerId);
    Order order = buildOrder(orderId, buildCustomer(UUID.randomUUID()), farmer, OrderStatus.CONFIRMED);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.confirmOrder(orderId, jwt))
        .isInstanceOf(BusinessException.class);
  }

  // ─── confirmDelivery ─────────────────────────────────────────────────────

  @Test
  void confirmDelivery_shouldSetDeliveredAtAndStatus() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);
    Order order = buildOrder(orderId, customer, buildFarmer(UUID.randomUUID()), OrderStatus.IN_DELIVERY);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(order)).thenReturn(order);

    orderService.confirmDelivery(orderId, jwt);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    assertThat(order.getDeliveredAt()).isNotNull();
    assertThat(order.getStatusHistory()).hasSize(1);
    assertThat(order.getStatusHistory().get(0).getOrder()).isEqualTo(order);
    verify(notificationService).createCustomerOrderDeliveredNotification(order);
  }

  @Test
  void confirmDelivery_shouldThrow_whenOrderNotInDelivery() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);
    Order order = buildOrder(orderId, customer, buildFarmer(UUID.randomUUID()), OrderStatus.PENDING);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.confirmDelivery(orderId, jwt))
        .isInstanceOf(BusinessException.class);
  }

  // ─── updateOrderStatus ───────────────────────────────────────────────────

  @Test
  void updateOrderStatus_shouldSetDeliveredAt_whenNewStatusIsDelivered() {
    UUID farmerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(farmerId, TypeUser.FARMER);
    Producer farmer = buildFarmer(farmerId);
    Order order = buildOrder(orderId, buildCustomer(UUID.randomUUID()), farmer, OrderStatus.IN_DELIVERY);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(order)).thenReturn(order);

    orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED, jwt);

    assertThat(order.getDeliveredAt()).isNotNull();
    org.mockito.ArgumentCaptor<br.com.ragro.domain.OrderStatusHistory> historyCaptor =
        org.mockito.ArgumentCaptor.forClass(br.com.ragro.domain.OrderStatusHistory.class);
    verify(orderStatusHistoryRepository).save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getOrder()).isEqualTo(order);
    assertThat(historyCaptor.getValue().getStatus()).isEqualTo(OrderStatus.DELIVERED);
    verify(notificationService).createCustomerOrderDeliveredNotification(order);
  }

  @Test
  void updateOrderStatus_shouldNotSetDeliveredAt_whenNewStatusIsNotDelivered() {
    UUID farmerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(farmerId, TypeUser.FARMER);
    Producer farmer = buildFarmer(farmerId);
    Order order = buildOrder(orderId, buildCustomer(UUID.randomUUID()), farmer, OrderStatus.PENDING);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(order)).thenReturn(order);

    orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED, jwt);

    assertThat(order.getDeliveredAt()).isNull();
    verify(notificationService).createCustomerOrderAcceptedNotification(order);
  }

  @Test
  void updateOrderStatus_shouldNotifyForCancelled() {
    UUID farmerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(farmerId, TypeUser.FARMER);
    Producer farmer = buildFarmer(farmerId);
    Order order = buildOrder(orderId, buildCustomer(UUID.randomUUID()), farmer, OrderStatus.PENDING);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(order)).thenReturn(order);

    orderService.updateOrderStatus(orderId, OrderStatus.CANCELLED, jwt);

    verify(notificationService).createCustomerOrderRefusedNotification(order);
  }

  @Test
  void updateOrderStatus_shouldNotifyForInDelivery() {
    UUID farmerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(farmerId, TypeUser.FARMER);
    Producer farmer = buildFarmer(farmerId);
    Order order = buildOrder(orderId, buildCustomer(UUID.randomUUID()), farmer, OrderStatus.CONFIRMED);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(order)).thenReturn(order);

    orderService.updateOrderStatus(orderId, OrderStatus.IN_DELIVERY, jwt);

    verify(notificationService).createCustomerOrderInDeliveryNotification(order);
  }

  @Test
  void updateOrderStatus_shouldThrow_whenFarmerDoesNotOwnOrder() {
    UUID farmerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(farmerId, TypeUser.FARMER);
    Producer otherFarmer = buildFarmer(UUID.randomUUID());
    Order order = buildOrder(orderId, buildCustomer(UUID.randomUUID()), otherFarmer, OrderStatus.PENDING);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED, jwt))
        .isInstanceOf(ForbiddenException.class);
  }

  // ─── findPrimaryPaymentMethod / createAddressSnapshot (via repeatOrder) ──

  @Test
  void repeatOrder_shouldReturnNullPaymentMethod_whenFarmerHasNoneActive() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);
    Producer farmer = buildFarmer(UUID.randomUUID());
    Order order = buildOrder(orderId, customer, farmer, OrderStatus.DELIVERED);

    Product product = buildProduct(new BigDecimal("4.00"), new BigDecimal("10"));
    OrderItem orderItem = new OrderItem();
    orderItem.setProduct(product);
    orderItem.setQuantity(BigDecimal.ONE);
    orderItem.setSubtotal(new BigDecimal("4.00")); 
    order.getItems().add(orderItem);

    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(cartRepository.findByCustomerIdAndActiveTrue(customerId)).thenReturn(Optional.empty());
    when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    when(paymentMethodRepository.findByFarmerIdAndActiveTrueOrderByCreatedAtAsc(farmer.getId()))
        .thenReturn(List.of());

    CartResponse response = orderService.repeatOrder(orderId, jwt);

    assertThat(response).isNotNull();
  }
 
  @Test
  void repeatOrder_shouldClampQuantity_toAvailableStock() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);
    Producer farmer = buildFarmer(UUID.randomUUID());
    Order order = buildOrder(orderId, customer, farmer, OrderStatus.DELIVERED);
 
    Product product = buildProduct(new BigDecimal("4.00"), new BigDecimal("2")); // only 2 in stock
    OrderItem orderItem = new OrderItem();
    orderItem.setProduct(product);
    orderItem.setQuantity(new BigDecimal("5")); // wants 5
    orderItem.setSubtotal(new BigDecimal("20.00"));
    order.getItems().add(orderItem);
 
    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(cartRepository.findByCustomerIdAndActiveTrue(customerId)).thenReturn(Optional.empty());
    when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    when(paymentMethodRepository.findByFarmerIdAndActiveTrueOrderByCreatedAtAsc(farmer.getId()))
        .thenReturn(List.of());
 
    CartResponse response = orderService.repeatOrder(orderId, jwt);
 
    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getItems().get(0).getQuantity()).isEqualByComparingTo("2");
  }
 
  @Test
  void repeatOrder_shouldSkipInactiveProducts() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);
    Producer farmer = buildFarmer(UUID.randomUUID());
    Order order = buildOrder(orderId, customer, farmer, OrderStatus.DELIVERED);
 
    Product inactiveProduct = buildProduct(new BigDecimal("4.00"), new BigDecimal("10"));
    inactiveProduct.setActive(false);
    OrderItem orderItem = new OrderItem();
    orderItem.setProduct(inactiveProduct);
    orderItem.setQuantity(BigDecimal.ONE);
    orderItem.setSubtotal(new BigDecimal("4.00"));
    order.getItems().add(orderItem);
 
    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(cartRepository.findByCustomerIdAndActiveTrue(customerId)).thenReturn(Optional.empty());
 
    assertThatThrownBy(() -> orderService.repeatOrder(orderId, jwt))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("disponível em estoque");
  }
 
  @Test
  void repeatOrder_shouldClearExistingCart_whenItBelongsToDifferentFarmer() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);
    Producer orderFarmer = buildFarmer(UUID.randomUUID());
    Producer existingCartFarmer = buildFarmer(UUID.randomUUID());
 
    Order order = buildOrder(orderId, customer, orderFarmer, OrderStatus.DELIVERED);
    Product product = buildProduct(new BigDecimal("4.00"), new BigDecimal("10"));
    OrderItem orderItem = new OrderItem();
    orderItem.setProduct(product);
    orderItem.setQuantity(BigDecimal.ONE);
    orderItem.setSubtotal(new BigDecimal("4.00"));
    order.getItems().add(orderItem);
 
    Cart existingCart = new Cart();
    existingCart.setFarmer(existingCartFarmer);
 
    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(cartRepository.findByCustomerIdAndActiveTrue(customerId))
        .thenReturn(Optional.of(existingCart));
    when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    when(paymentMethodRepository.findByFarmerIdAndActiveTrueOrderByCreatedAtAsc(orderFarmer.getId()))
        .thenReturn(List.of());
 
    orderService.repeatOrder(orderId, jwt);
 
    verify(cartService).clearCart(customer);
    verify(cartRepository).flush();
  }
 
  @Test
  void repeatOrder_shouldThrow_whenCustomerDoesNotOwnOrder() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    User user = buildUser(customerId, TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerId);
    Order order = buildOrder(orderId, buildCustomer(UUID.randomUUID()), buildFarmer(UUID.randomUUID()), OrderStatus.DELIVERED);
 
    Jwt jwt = jwt();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
 
    assertThatThrownBy(() -> orderService.repeatOrder(orderId, jwt))
        .isInstanceOf(ForbiddenException.class);
  }

  // ─── helpers (added to support the extra test cases below) ─────────────

  private User buildUser(UUID id, TypeUser type) {
    User builtUser = new User();
    builtUser.setId(id);
    builtUser.setName("Test User");
    builtUser.setType(type);
    builtUser.setActive(true);
    return builtUser;
  }

  private Customer buildCustomer(UUID id) {
    User customerUser = new User();
    customerUser.setId(id);
    customerUser.setName("Test Customer");
    customerUser.setType(TypeUser.CUSTOMER);
    customerUser.setActive(true);

    Customer builtCustomer = new Customer();
    builtCustomer.setId(id);
    builtCustomer.setUser(customerUser);
    return builtCustomer;
  }

  private Producer buildFarmer(UUID id) {
    User farmerUser = new User();
    farmerUser.setId(id);
    farmerUser.setName("Test Farmer");
    farmerUser.setType(TypeUser.FARMER);
    farmerUser.setActive(true);

    Producer builtFarmer = new Producer();
    builtFarmer.setId(id);
    builtFarmer.setFarmName("Farm Test");
    builtFarmer.setUser(farmerUser);
    return builtFarmer;
  }

  private Product buildProduct(BigDecimal price, BigDecimal stockQuantity) {
    Product builtProduct = new Product();
    builtProduct.setId(UUID.randomUUID());
    builtProduct.setName("Tomate");
    builtProduct.setPrice(price);
    builtProduct.setUnityType("kg");
    builtProduct.setStockQuantity(stockQuantity);
    builtProduct.setActive(true);
    return builtProduct;
  }

  private Address buildAddress() {
    Address builtAddress = new Address();
    builtAddress.setId(UUID.randomUUID());
    builtAddress.setStreet("Rua das Flores");
    builtAddress.setNumber("123");
    builtAddress.setNeighborhood("Centro");
    builtAddress.setCity("Porto Alegre");
    builtAddress.setState("RS");
    builtAddress.setZipCode("90010120");
    builtAddress.setPrimary(true);
    return builtAddress;
  }

  private Order buildOrder(
      UUID orderId, Customer orderCustomer, Producer orderFarmer, OrderStatus status) {
    Order builtOrder = new Order();
    builtOrder.setId(orderId);
    builtOrder.setCustomer(orderCustomer);
    builtOrder.setFarmer(orderFarmer);
    builtOrder.setStatus(status);
    builtOrder.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    builtOrder.setPaymentMethod(paymentMethod);
    builtOrder.setSeenByFarmer(false);
    return builtOrder;
  }
}
