package br.com.ragro.service;

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
import br.com.ragro.mapper.CartMapper;
import br.com.ragro.mapper.OrderMapper;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.CartRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.OrderStatusHistoryRepository;
import br.com.ragro.repository.PaymentMethodRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final UserService userService;
  private final CustomerRepository customerRepository;
  private final CartRepository cartRepository;
  private final CartService cartService;
  private final AddressRepository addressRepository;
  private final PaymentMethodRepository paymentMethodRepository;
  private final StockMovementService stockMovementService;
  private final OrderRepository orderRepository;
  private final OrderStatusHistoryRepository orderStatusHistoryRepository;

  @Transactional
  public OrderResponse createOrderFromCart(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas consumidores podem criar pedidos");
    }

    Customer customer = customerRepository.findById(user.getId())
        .orElseThrow(() -> new NotFoundException("Dados do consumidor não encontrados"));

    Cart cart = cartRepository.findByCustomerIdAndActiveTrue(customer.getId())
        .orElseThrow(() -> new BusinessException("Carrinho vazio ou não encontrado"));

    if (cart.getItems().stream().noneMatch(CartItem::isActive)) {
      throw new BusinessException("Seu carrinho não possui itens ativos");
    }

    Address deliveryAddress = getDeliveryAddress(customer);
    PaymentMethod paymentMethod = getPaymentMethod(cart.getFarmer());

    Order order = new Order();
    order.setCustomer(customer);
    order.setFarmer(cart.getFarmer());
    order.setDeliveryAddress(deliveryAddress);
    order.setDeliveryAddressSnapshot(createAddressSnapshot(deliveryAddress));
    order.setPaymentMethod(paymentMethod);
    order.setStatus(OrderStatus.PENDING);
    order.setPaymentStatus(PaymentStatus.PENDING);
    order.setNotes(null);

    cart.getItems().stream().filter(CartItem::isActive).forEach(cartItem -> {
      Product product = cartItem.getProduct();

      OrderItem orderItem = new OrderItem();
      orderItem.setOrder(order);
      orderItem.setProduct(product);
      orderItem.setProductNameSnapshot(product.getName());
      orderItem.setUnitPriceSnapshot(product.getPrice());
      orderItem.setUnityTypeSnapshot(product.getUnityType());
      orderItem.setQuantity(cartItem.getQuantity());
      orderItem.setSubtotal(product.getPrice().multiply(cartItem.getQuantity()));
      order.getItems().add(orderItem);
    });

    OrderStatusHistory history = new OrderStatusHistory();
    history.setOrder(order);
    history.setStatus(OrderStatus.PENDING);
    order.getStatusHistory().add(history);

    Order savedOrder = orderRepository.saveAndFlush(order);
    cartService.clearCart(customer);
    return OrderMapper.toResponse(savedOrder);
  }

  @Transactional
  public OrderResponse cancelOrder(UUID orderId, Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER && user.getType() != TypeUser.FARMER) {
      throw new ForbiddenException("Apenas consumidores ou produtores podem cancelar pedidos");
    }

    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new NotFoundException("Pedido não encontrado"));

    if (user.getType() == TypeUser.CUSTOMER && !order.getCustomer().getId().equals(user.getId())) {
      throw new ForbiddenException("Você não tem permissão para cancelar este pedido");
    }

    if (user.getType() == TypeUser.FARMER && !order.getFarmer().getId().equals(user.getId())) {
      throw new ForbiddenException("Você não tem permissão para cancelar este pedido");
    }

    if (order.getStatus() != OrderStatus.PENDING) {
      throw new BusinessException("Somente pedidos com status PENDING podem ser cancelados");
    }

    order.setStatus(OrderStatus.CANCELLED);

    order.getItems().forEach(item -> {
      stockMovementService.registerCancelledSale(
          item.getProduct(), 
          item.getQuantity(), 
          "Pedido cancelado"
      );
    });

    OrderStatusHistory history = new OrderStatusHistory();
    history.setOrder(order);
    history.setStatus(OrderStatus.CANCELLED);
    order.getStatusHistory().add(history);

    Order savedOrder = orderRepository.saveAndFlush(order);
    return OrderMapper.toResponse(savedOrder);
  }

  @Transactional(readOnly = true)
  public List<CustomerOrderResponse> getMyOrders(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas consumidores podem visualizar seus pedidos");
    }

    customerRepository.findById(user.getId())
        .orElseThrow(() -> new NotFoundException("Dados do consumidor não encontrados"));

    return orderRepository.findByCustomerIdOrderByCreatedAtDesc(user.getId()).stream()
        .map(OrderMapper::toCustomerOrderResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> getProducerOrders(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.FARMER) {
      throw new ForbiddenException("Apenas produtores podem visualizar pedidos recebidos");
    }

    return orderRepository.findByFarmerIdOrderByCreatedAtDesc(user.getId()).stream()
        .map(OrderMapper::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public CustomerOrderResponse getMyOrderById(UUID orderId, Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas consumidores podem visualizar seus pedidos");
    }

    customerRepository.findById(user.getId())
        .orElseThrow(() -> new NotFoundException("Dados do consumidor não encontrados"));

    Order order = orderRepository.findByIdAndCustomerId(orderId, user.getId())
        .orElseThrow(() -> new NotFoundException("Pedido não encontrado para este consumidor"));

    return OrderMapper.toCustomerOrderResponse(order);
  }

  @Transactional
  public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus, Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.FARMER) {
      throw new ForbiddenException("Apenas produtores podem atualizar o status do pedido");
    }

    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new NotFoundException("Pedido não encontrado"));

    if (!order.getFarmer().getId().equals(user.getId())) {
      throw new ForbiddenException("Você não tem permissão para atualizar este pedido");
    }

    order.setStatus(newStatus);
    Order updatedOrder = orderRepository.saveAndFlush(order);

    OrderStatusHistory history = new OrderStatusHistory();
    history.setOrder(updatedOrder);
    history.setStatus(newStatus);
    orderStatusHistoryRepository.save(history);

    return OrderMapper.toResponse(updatedOrder);
  }

  @Transactional
  public OrderResponse confirmOrder(UUID orderId, Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.FARMER) {
      throw new ForbiddenException("Apenas produtores podem confirmar pedidos");
    }

    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new NotFoundException("Pedido não encontrado"));

    if (!order.getFarmer().getId().equals(user.getId())) {
      throw new ForbiddenException("Você não tem permissão para confirmar este pedido");
    }

    if (order.getStatus() != OrderStatus.PENDING) {
      throw new BusinessException("Somente pedidos com status PENDING podem ser confirmados");
    }

    order.getItems().forEach(
        item -> stockMovementService.registerSale(
            item.getProduct(), item.getQuantity(), "Pedido confirmado"));

    order.setStatus(OrderStatus.CONFIRMED);
    Order updatedOrder = orderRepository.saveAndFlush(order);

    OrderStatusHistory history = new OrderStatusHistory();
    history.setOrder(updatedOrder);
    history.setStatus(OrderStatus.CONFIRMED);
    orderStatusHistoryRepository.save(history);

    return OrderMapper.toResponse(updatedOrder);
  }

  private Address getDeliveryAddress(Customer customer) {
    return addressRepository.findByUserIdAndIsPrimaryTrue(customer.getId())
        .orElseThrow(() -> new BusinessException("Nenhum endereço principal cadastrado para o cliente"));
  }

  private PaymentMethod getPaymentMethod(Producer farmer) {
    List<PaymentMethod> methods = paymentMethodRepository.findByFarmerIdAndActiveTrue(farmer.getId());
    if (methods.isEmpty()) {
      throw new BusinessException("O produtor não possui nenhum método de pagamento ativo");
    }
    return methods.get(0);
  }

  private AddressSnapshot createAddressSnapshot(Address address) {
    return AddressSnapshot.builder()
        .street(address.getStreet())
        .number(address.getNumber())
        .complement(address.getComplement())
        .neighborhood(address.getNeighborhood())
        .city(address.getCity())
        .state(address.getState())
        .zipCode(address.getZipCode())
        .latitude(address.getLatitude())
        .longitude(address.getLongitude())
        .build();
  }

  @Transactional
  public CartResponse repeatOrder(UUID orderId, Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas consumidores podem repetir pedidos");
    }

    Customer customer = customerRepository.findById(user.getId())
        .orElseThrow(() -> new NotFoundException("Dados do consumidor não encontrados"));

    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new NotFoundException("Pedido não encontrado"));

    if (!order.getCustomer().getId().equals(user.getId())) {
      throw new ForbiddenException("Você não tem permissão para repetir este pedido");
    }

    Cart cart = cartRepository.findByCustomerIdAndActiveTrue(customer.getId())
        .orElse(null);

    if (cart != null && !cart.getFarmer().getId().equals(order.getFarmer().getId())) {
      cartService.clearCart(customer);
      cartRepository.flush();
      cart = null;
    }

    if (cart == null) {
      cart = new Cart();
      cart.setCustomer(customer);
      cart.setFarmer(order.getFarmer());
      cart.setActive(true);
    }

    for (OrderItem orderItem : order.getItems()) {
      Product product = orderItem.getProduct();
      if (product.isActive() && product.getStockQuantity().compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal quantityToAdd = orderItem.getQuantity();
        BigDecimal currentQuantityInCart = BigDecimal.ZERO;

        Optional<CartItem> existingItemOpt = cart.getItems().stream()
            .filter(item -> item.isActive() && item.getProduct().getId().equals(product.getId()))
            .findFirst();

        if (existingItemOpt.isPresent()) {
          currentQuantityInCart = existingItemOpt.get().getQuantity();
        }

        BigDecimal targetTotalQuantity = currentQuantityInCart.add(quantityToAdd);
        if (targetTotalQuantity.compareTo(product.getStockQuantity()) > 0) {
          quantityToAdd = product.getStockQuantity().subtract(currentQuantityInCart);
        }

        if (quantityToAdd.compareTo(BigDecimal.ZERO) > 0) {
          if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            existingItem.setQuantity(existingItem.getQuantity().add(quantityToAdd));
          } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(quantityToAdd);
            newItem.setActive(true);
            cart.getItems().add(newItem);
          }
        }
      }
    }

    if (cart.getItems().isEmpty() || cart.getItems().stream().noneMatch(CartItem::isActive)) {
      throw new BusinessException("Nenhum item do pedido está disponível em estoque no momento");
    }

    Cart savedCart = cartRepository.saveAndFlush(cart);
    return CartMapper.toResponse(savedCart);
  }
}
