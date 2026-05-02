package br.com.ragro.service;

import br.com.ragro.controller.response.OrderResponse;
import br.com.ragro.domain.*;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.PaymentStatus;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.OrderMapper;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.CartRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.OrderStatusHistoryRepository;
import br.com.ragro.repository.PaymentMethodRepository;
import java.util.List;
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
      
      stockMovementService.registerSale(product, cartItem.getQuantity(), "Pedido criado a partir do carrinho");

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
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas consumidores podem cancelar pedidos");
    }

    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new NotFoundException("Pedido não encontrado"));

    if (!order.getCustomer().getId().equals(user.getId())) {
      throw new ForbiddenException("Você não tem permissão para cancelar este pedido");
    }

    if (order.getStatus() != OrderStatus.PENDING) {
      throw new BusinessException("Somente pedidos com status PENDING podem ser cancelados");
    }

    order.setStatus(OrderStatus.CANCELLED);

    OrderStatusHistory history = new OrderStatusHistory();
    history.setOrder(order);
    history.setStatus(OrderStatus.CANCELLED);
    order.getStatusHistory().add(history);

    Order savedOrder = orderRepository.saveAndFlush(order);

    return OrderMapper.toResponse(savedOrder);
  }

  @Transactional(readOnly = true)
  public OrderResponse getMyOrderById(UUID orderId, Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas consumidores podem visualizar seus pedidos");
    }

    customerRepository.findById(user.getId())
        .orElseThrow(() -> new NotFoundException("Dados do consumidor não encontrados"));

    Order order = orderRepository.findByIdAndCustomerId(orderId, user.getId())
        .orElseThrow(() -> new NotFoundException("Pedido não encontrado para este consumidor"));

    return OrderMapper.toResponse(order);
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
}
