package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        .hasMessageContaining("Dados do consumidor");
  }

  @Test
  void shouldThrowBusinessException_whenCartIsEmptyOrNotFound() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Carrinho vazio");
  }

  @Test
  void shouldThrowBusinessException_whenCartHasNoActiveItems() {
    cartItem.setActive(false);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(cart));

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("itens ativos");
  }

  @Test
  void shouldThrowBusinessException_whenPrimaryAddressNotFound() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(cart));
    when(addressRepository.findByUserIdAndIsPrimaryTrue(customer.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.createOrderFromCart(jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("principal cadastrado");
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
        .hasMessageContaining("pagamento ativo");
  }

  @Test
  void shouldCreateOrderAndClearCart_whenValidData() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(cart));
    when(addressRepository.findByUserIdAndIsPrimaryTrue(customer.getId())).thenReturn(Optional.of(address));
    when(paymentMethodRepository.findByFarmerIdAndActiveTrue(farmer.getId())).thenReturn(List.of(paymentMethod));
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

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

    OrderResponse response = orderService.cancelOrder(order.getId(), jwt());
    assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verify(stockMovementService, times(1)).registerCancelledSale(eq(product), eq(new BigDecimal("2.00")), anyString());
  }

  @Test
  void shouldThrowForbidden_whenNonCustomerTriesToCancel() {
    user.setType(TypeUser.ADMIN);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    assertThatThrownBy(() -> orderService.cancelOrder(UUID.randomUUID(), jwt()))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldThrowNotFound_whenOrderDoesNotExist() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    UUID fakeId = UUID.randomUUID();
    when(orderRepository.findById(fakeId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.cancelOrder(fakeId, jwt()))
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

    assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), jwt()))
        .isInstanceOf(ForbiddenException.class);
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

    OrderResponse response = orderService.cancelOrder(order.getId(), jwt());

    assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
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

    assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), jwt()))
        .isInstanceOf(ForbiddenException.class);
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
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.empty());
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
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(cart));
    when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(inv -> {
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
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.empty());
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
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.repeatOrder(orderId, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("dispon");
  }

  private Jwt jwt() {
    return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300),
        Map.of("alg", "none"), Map.of("sub", "sub"));
  }
}
