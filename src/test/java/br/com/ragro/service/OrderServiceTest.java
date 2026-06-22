package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import br.com.ragro.domain.PaymentMethod;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.domain.event.OrderStatusChangedEvent;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.CartRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.OrderRepository;
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
  @Mock private ReviewRepository reviewRepository;
  @Mock private MinioStorageService storageService;
  @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

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

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Apenas consumidores podem criar pedidos");
  }

  @Test
  void shouldThrowNotFound_whenCustomerProfileNotFound() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    when(customerRepository.findById(user.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Dados do consumidor");
  }

  @Test
  void shouldThrowBusinessException_whenCartIsEmptyOrNotFound() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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

    assertThatThrownBy(() -> orderService.cancelOrder(UUID.randomUUID(), jwt(), null))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldThrowNotFound_whenOrderDoesNotExist() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.refuseOrderAsFarmer(order.getId(), jwt(), null);

    assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getCancellationReason()).isEqualTo("REFUSED_BY_FARMER");
    verify(stockMovementService, never()).registerCancelledSale(any(), any(), anyString());
    verify(eventPublisher)
        .publishEvent(
            argThat(
                (OrderStatusChangedEvent e) ->
                    e.newStatus() == OrderStatus.CANCELLED
                        && e.initiatedBy() == TypeUser.FARMER));
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
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.confirmDelivery(order.getId(), jwt());

    assertThat(response.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    assertThat(order.getStatusHistory()).hasSize(1);
    assertThat(order.getStatusHistory().get(0).getStatus()).isEqualTo(OrderStatus.DELIVERED);
    verify(eventPublisher)
        .publishEvent(
            argThat((OrderStatusChangedEvent e) -> e.newStatus() == OrderStatus.DELIVERED));
  }

  @Test
  void shouldThrowForbidden_whenNonCustomerConfirmsDelivery() {
    user.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    // Máquina de estados: IN_DELIVERY só é alcançável a partir de CONFIRMED (antes o método
    // aceitava qualquer transição — comportamento corrigido pela auditoria Fase 0, achado A3).
    order.setStatus(OrderStatus.CONFIRMED);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response =
        orderService.updateOrderStatus(orderId, OrderStatus.IN_DELIVERY, jwt());

    assertThat(response.getStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
    assertThat(order.getStatusHistory())
        .anyMatch(h -> h.getStatus() == OrderStatus.IN_DELIVERY);
    verify(eventPublisher)
        .publishEvent(
            argThat((OrderStatusChangedEvent e) -> e.newStatus() == OrderStatus.IN_DELIVERY));
  }

  @Test
  void shouldThrowForbidden_whenNonFarmerUpdatesOrderStatus() {
    user.setType(TypeUser.CUSTOMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED, jwt()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Pedido");
  }

  @Test
  void updateOrderStatus_shouldRejectInvalidTransition_whenSkippingStates() {
    user.setType(TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();
    farmer.setId(user.getId());

    Order order = new Order();
    order.setId(orderId);
    order.setFarmer(farmer);
    order.setCustomer(customer);
    order.setStatus(OrderStatus.PENDING);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    // PENDING → DELIVERED pula CONFIRMED/IN_DELIVERY (e o débito de estoque) — rejeitado.
    assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Transição de status inválida");
    verify(orderRepository, never()).saveAndFlush(any(Order.class));
  }

  @Test
  void updateOrderStatus_shouldRejectTransition_whenOrderAlreadyDelivered() {
    user.setType(TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();
    farmer.setId(user.getId());

    Order order = new Order();
    order.setId(orderId);
    order.setFarmer(farmer);
    order.setCustomer(customer);
    order.setStatus(OrderStatus.DELIVERED);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(
            () -> orderService.updateOrderStatus(orderId, OrderStatus.IN_DELIVERY, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Transição de status inválida");
  }

  @Test
  void updateOrderStatus_shouldRejectPendingAsTarget() {
    user.setType(TypeUser.FARMER);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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

    assertThatThrownBy(
            () -> orderService.updateOrderStatus(UUID.randomUUID(), OrderStatus.PENDING, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("não pode voltar para PENDING");
  }

  @Test
  void updateOrderStatus_shouldDebitStock_whenTargetIsConfirmed() {
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
    OrderItem item = new OrderItem();
    item.setOrder(order);
    item.setProduct(product);
    item.setQuantity(new BigDecimal("2.00"));
    item.setSubtotal(new BigDecimal("10.00"));
    order.getItems().add(item);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    // PATCH /status com CONFIRMED delega para confirmOrder: mesmo efeito de estoque do
    // endpoint dedicado (antes, este caminho confirmava SEM debitar — achado A3).
    OrderResponse response = orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED, jwt());

    assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(stockMovementService).registerSale(eq(product), eq(new BigDecimal("2.00")), anyString());
  }

  @Test
  void updateOrderStatus_shouldRestoreStockAndRecordReason_whenTargetIsCancelled() {
    user.setType(TypeUser.FARMER);
    UUID orderId = UUID.randomUUID();
    farmer.setId(user.getId());

    Order order = new Order();
    order.setId(orderId);
    order.setFarmer(farmer);
    order.setCustomer(customer);
    order.setStatus(OrderStatus.CONFIRMED);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);
    OrderItem item = new OrderItem();
    item.setOrder(order);
    item.setProduct(product);
    item.setQuantity(new BigDecimal("2.00"));
    item.setSubtotal(new BigDecimal("10.00"));
    order.getItems().add(item);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    // PATCH /status com CANCELLED delega para a recusa: devolve estoque debitado e grava o
    // motivo (antes, este caminho cancelava sem devolver estoque nem auditar — achado A3).
    OrderResponse response = orderService.updateOrderStatus(orderId, OrderStatus.CANCELLED, jwt());

    assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getCancellationReason()).isEqualTo("REFUSED_BY_FARMER");
    verify(stockMovementService)
        .registerCancelledSale(eq(product), eq(new BigDecimal("2.00")), anyString());
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

    assertThatThrownBy(() -> orderService.getMyOrderById(UUID.randomUUID(), jwt()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Apenas consumidores podem visualizar seus pedidos");
  }

  @Test
  void shouldThrowNotFound_whenOrderByIdDoesNotBelongToCustomer() {
    UUID orderId = UUID.randomUUID();
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.confirmOrder(orderId, jwt());

    assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(stockMovementService).registerSale(eq(product), eq(new BigDecimal("2.00")), anyString());
    assertThat(order.getStatusHistory()).anyMatch(h -> h.getStatus() == OrderStatus.CONFIRMED);
    verify(eventPublisher)
        .publishEvent(
            argThat((OrderStatusChangedEvent e) -> e.newStatus() == OrderStatus.CONFIRMED));
  }

  @Test
  void shouldThrowForbidden_whenNonFarmerConfirmsOrder() {
    user.setType(TypeUser.CUSTOMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.confirmOrder(orderId, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Somente pedidos com status PENDING podem ser confirmados");
  }

  @Test
  void shouldThrowForbidden_whenNonCustomerTriesToRepeatOrder() {
    user.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
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
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.repeatOrder(orderId, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("dispon");
  }

  // ---------- confirmDeliveryWithCode (produtor confirma com o código do consumidor) ----------

  @Test
  void confirmDeliveryWithCode_shouldTransitionToDelivered_whenCodeMatches() {
    Order order = buildFarmerOwnedInDeliveryOrder("1234");
    stubFarmerAuthenticated();
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response = orderService.confirmDeliveryWithCode(order.getId(), "1234", jwt());

    assertThat(response.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    assertThat(order.getDeliveredAt()).isNotNull();
    assertThat(order.getConfirmationAttempts()).isZero();
    verify(eventPublisher)
        .publishEvent(any(br.com.ragro.domain.event.OrderStatusChangedEvent.class));
  }

  @Test
  void confirmDeliveryWithCode_shouldIncrementAttempts_whenCodeWrong() {
    Order order = buildFarmerOwnedInDeliveryOrder("1234");
    stubFarmerAuthenticated();
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(() -> orderService.confirmDeliveryWithCode(order.getId(), "0000", jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("incorreto");
    assertThat(order.getConfirmationAttempts()).isEqualTo(1);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
  }

  @Test
  void confirmDeliveryWithCode_shouldLock_whenMaxAttemptsReached() {
    Order order = buildFarmerOwnedInDeliveryOrder("1234");
    order.setConfirmationAttempts(4); // a próxima tentativa errada atinge o limite (5)
    stubFarmerAuthenticated();
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(() -> orderService.confirmDeliveryWithCode(order.getId(), "0000", jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("bloqueado");
    assertThat(order.getConfirmationLockedUntil()).isNotNull();
  }

  @Test
  void confirmDeliveryWithCode_shouldReject_whenLocked() {
    Order order = buildFarmerOwnedInDeliveryOrder("1234");
    order.setConfirmationLockedUntil(java.time.OffsetDateTime.now().plusMinutes(10));
    stubFarmerAuthenticated();
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.confirmDeliveryWithCode(order.getId(), "1234", jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("tentativas");
  }

  @Test
  void confirmDeliveryWithCode_shouldRejectNonFarmer() {
    user.setType(TypeUser.CUSTOMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(
            () -> orderService.confirmDeliveryWithCode(UUID.randomUUID(), "1234", jwt()))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void updateOrderStatus_shouldGenerateConfirmationCode_onTransitionToInDelivery() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.CONFIRMED);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);
    stubFarmerAuthenticated();
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    orderService.updateOrderStatus(order.getId(), OrderStatus.IN_DELIVERY, jwt());

    assertThat(order.getConfirmationCode()).isNotNull().hasSize(4);
    assertThat(order.getConfirmationAttempts()).isZero();
  }

  private void stubFarmerAuthenticated() {
    user.setType(TypeUser.FARMER);
    farmer.setId(user.getId());
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    lenient()
        .when(
            userService.requireRole(
                any(), any(), org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv -> {
              User authenticated = userService.getAuthenticatedUser(inv.getArgument(0));
              if (authenticated.getType() != inv.<TypeUser>getArgument(1)) {
                throw new ForbiddenException(inv.getArgument(2));
              }
              return authenticated;
            });
  }

  private Order buildFarmerOwnedInDeliveryOrder(String code) {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer);
    order.setFarmer(farmer);
    order.setStatus(OrderStatus.IN_DELIVERY);
    order.setConfirmationCode(code);
    order.setConfirmationAttempts(0);
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().city("Test City").build());
    order.setPaymentMethod(paymentMethod);
    OrderItem item = new OrderItem();
    item.setProduct(product);
    item.setProductNameSnapshot("Product Test");
    item.setUnitPriceSnapshot(new BigDecimal("10.00"));
    item.setQuantity(new BigDecimal("2.00"));
    item.setSubtotal(new BigDecimal("20.00"));
    order.getItems().add(item);
    return order;
  }

  private Jwt jwt() {
    return new Jwt(
        "token",
        Instant.now(),
        Instant.now().plusSeconds(300),
        Map.of("alg", "none"),
        Map.of("sub", "sub"));
  }
}
