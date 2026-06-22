package br.com.ragro.service;

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
import br.com.ragro.domain.event.OrderStatusChangedEvent;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.CartMapper;
import br.com.ragro.mapper.OrderMapper;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.CartRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.PaymentMethodRepository;
import br.com.ragro.repository.ReviewRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  /**
   * Máquina de estados única do pedido. Toda transição passa por {@link #applyTransition} (ou por
   * {@link #applyCancellation}, que valida via {@link #isCancellableStatus}); antes desta tabela,
   * {@code updateOrderStatus} aceitava qualquer transição sem efeitos de estoque/auditoria
   * (auditoria Fase 0, achado A3).
   */
  private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS =
      Map.of(
          OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
          OrderStatus.CONFIRMED, Set.of(OrderStatus.IN_DELIVERY, OrderStatus.CANCELLED),
          OrderStatus.IN_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
          OrderStatus.DELIVERED, Set.of(),
          OrderStatus.CANCELLED, Set.of());

  private static final int DEFAULT_PAGE_SIZE = 20;

  private static final int MAX_CONFIRMATION_ATTEMPTS = 5;
  private static final int LOCKOUT_MINUTES = 15;

  private final UserService userService;
  private final CustomerRepository customerRepository;
  private final CartRepository cartRepository;
  private final CartService cartService;
  private final AddressRepository addressRepository;
  private final PaymentMethodRepository paymentMethodRepository;
  private final StockMovementService stockMovementService;
  private final OrderRepository orderRepository;
  private final ReviewRepository reviewRepository;
  private final MinioStorageService storageService;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public OrderResponse createOrderFromCart(Jwt jwt) {
    User user = userService.requireRole(jwt, TypeUser.CUSTOMER, "Apenas consumidores podem criar pedidos");

    Customer customer =
        customerRepository
            .findById(user.getId())
            .orElseThrow(() -> new NotFoundException("Dados do consumidor não encontrados"));

    Cart cart =
        cartRepository
            .findByCustomerIdAndActiveTrue(customer.getId())
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

    cart.getItems().stream()
        .filter(CartItem::isActive)
        .forEach(
            cartItem -> {
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

    appendStatusHistory(order, OrderStatus.PENDING);

    Order savedOrder = orderRepository.saveAndFlush(order);
    cartService.clearCart(customer);
    eventPublisher.publishEvent(
        new OrderStatusChangedEvent(savedOrder, null, OrderStatus.PENDING, TypeUser.CUSTOMER));
    return OrderMapper.toResponse(savedOrder, storageService);
  }

  /**
   * Legacy cancel entry-point (backward compatible with {@code PATCH /orders/{id}/cancel}). Routes
   * to the role-specific flow: CUSTOMER cancels their own order, FARMER refuses an incoming one.
   * Stock is credited back only for orders that had already debited it (CONFIRMED/IN_DELIVERY);
   * PENDING orders never debit stock (D26), so cancelling them leaves stock untouched.
   */
  @Transactional
  public OrderResponse cancelOrder(UUID orderId, Jwt jwt, CancelOrderRequest request) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() == TypeUser.CUSTOMER) {
      return cancelOrderAsCustomer(orderId, jwt, request);
    }
    if (user.getType() == TypeUser.FARMER) {
      return refuseOrderAsFarmer(orderId, jwt, request);
    }
    throw new ForbiddenException("Apenas consumidores ou produtores podem cancelar pedidos");
  }

  /**
   * Customer cancels their own order (PENDING, CONFIRMED or IN_DELIVERY). Stock debited at
   * confirmation is restored via {@link #applyCancellation}; PENDING orders never debited it.
   */
  @Transactional
  public OrderResponse cancelOrderAsCustomer(UUID orderId, Jwt jwt, CancelOrderRequest request) {
    Order order =
        loadOrderOwnedByCustomer(orderId, jwt, "Apenas consumidores podem cancelar seus pedidos");

    if (!isCancellableStatus(order.getStatus())) {
      throw new BusinessException(
          "Somente pedidos com status PENDING, CONFIRMED ou IN_DELIVERY podem ser cancelados");
    }

    OrderStatus previousStatus = order.getStatus();
    boolean applied = applyCancellation(order, request, "CUSTOMER_CANCELLED");
    Order savedOrder = orderRepository.saveAndFlush(order);
    if (applied) {
      eventPublisher.publishEvent(
          new OrderStatusChangedEvent(
              savedOrder, previousStatus, OrderStatus.CANCELLED, TypeUser.CUSTOMER));
    }
    return OrderMapper.toResponse(savedOrder, storageService);
  }

  /**
   * Farmer refuses an incoming order. Records {@code REFUSED_BY_FARMER} so the history stays
   * distinguishable from a customer-driven cancellation.
   */
  @Transactional
  public OrderResponse refuseOrderAsFarmer(UUID orderId, Jwt jwt, CancelOrderRequest request) {
    Order order = loadOrderOwnedByFarmer(orderId, jwt, "Apenas produtores podem recusar pedidos");

    if (!isCancellableStatus(order.getStatus())) {
      throw new BusinessException(
          "Somente pedidos com status PENDING, CONFIRMED ou IN_DELIVERY podem ser recusados");
    }

    OrderStatus previousStatus = order.getStatus();
    boolean applied = applyCancellation(order, request, "REFUSED_BY_FARMER");
    Order savedOrder = orderRepository.saveAndFlush(order);
    if (applied) {
      eventPublisher.publishEvent(
          new OrderStatusChangedEvent(
              savedOrder, previousStatus, OrderStatus.CANCELLED, TypeUser.FARMER));
    }
    return OrderMapper.toResponse(savedOrder, storageService);
  }

  /**
   * Customer confirms that an in-delivery order was received. Transitions IN_DELIVERY → DELIVERED.
   */
  @Transactional
  public OrderResponse confirmDelivery(UUID orderId, Jwt jwt) {
    Order order =
        loadOrderOwnedByCustomer(orderId, jwt, "Apenas consumidores podem confirmar a entrega");

    if (order.getStatus() != OrderStatus.IN_DELIVERY) {
      throw new BusinessException(
          "Somente pedidos em entrega podem ser confirmados como entregues");
    }

    OrderStatus previousStatus = order.getStatus();
    applyTransition(order, OrderStatus.DELIVERED);

    Order savedOrder = orderRepository.saveAndFlush(order);
    eventPublisher.publishEvent(
        new OrderStatusChangedEvent(
            savedOrder, previousStatus, OrderStatus.DELIVERED, TypeUser.CUSTOMER));
    return OrderMapper.toResponse(savedOrder, storageService);
  }

  /**
   * Produtor confirma que um pedido IN_DELIVERY foi recebido pelo consumidor, validado por um
   * código de 4 dígitos exibido no app do consumidor. Transiciona IN_DELIVERY → DELIVERED, com
   * proteção anti-brute-force (bloqueio após {@value #MAX_CONFIRMATION_ATTEMPTS} tentativas por
   * {@value #LOCKOUT_MINUTES} minutos).
   */
  @Transactional
  public OrderResponse confirmDeliveryWithCode(UUID orderId, String code, Jwt jwt) {
    Order order =
        loadOrderOwnedByFarmer(
            orderId, jwt, "Apenas produtores podem confirmar a entrega com código");

    if (order.getStatus() != OrderStatus.IN_DELIVERY) {
      throw new BusinessException(
          "Somente pedidos em entrega podem ser confirmados como entregues");
    }

    OffsetDateTime now = OffsetDateTime.now();
    if (order.getConfirmationLockedUntil() != null
        && now.isBefore(order.getConfirmationLockedUntil())) {
      throw new BusinessException(
          "Muitas tentativas incorretas. Tente novamente em alguns minutos.");
    }

    if (order.getConfirmationCode() == null || !order.getConfirmationCode().equals(code)) {
      int attempts = order.getConfirmationAttempts() + 1;
      order.setConfirmationAttempts(attempts);
      if (attempts >= MAX_CONFIRMATION_ATTEMPTS) {
        order.setConfirmationLockedUntil(now.plusMinutes(LOCKOUT_MINUTES));
        orderRepository.saveAndFlush(order);
        throw new BusinessException(
            "Código incorreto. Muitas tentativas: pedido bloqueado por "
                + LOCKOUT_MINUTES
                + " minutos.");
      }
      orderRepository.saveAndFlush(order);
      throw new BusinessException("Código de confirmação incorreto");
    }

    // Código correto — zera os contadores antes de confirmar.
    order.setConfirmationAttempts(0);
    order.setConfirmationLockedUntil(null);

    OrderStatus previousStatus = order.getStatus();
    applyTransition(order, OrderStatus.DELIVERED);
    Order savedOrder = orderRepository.saveAndFlush(order);
    eventPublisher.publishEvent(
        new OrderStatusChangedEvent(
            savedOrder, previousStatus, OrderStatus.DELIVERED, TypeUser.FARMER));
    return OrderMapper.toResponse(savedOrder, storageService);
  }

  /** Gera um código de confirmação aleatório de 4 dígitos com zero à esquerda (ex.: "0042"). */
  private String generateConfirmationCode() {
    return String.format("%04d", new Random().nextInt(10000));
  }

  /**
   * Applies the cancellation effects (status, reason/details, stock restore, history). Returns
   * {@code false} when the order was already cancelled (idempotent no-op, no event should fire).
   */
  private boolean applyCancellation(Order order, CancelOrderRequest request, String defaultReason) {
    if (order.getStatus() == OrderStatus.CANCELLED) {
      return false; // idempotent: already cancelled, don't re-apply or duplicate history
    }

    OrderStatus previousStatus = order.getStatus();
    order.setStatus(OrderStatus.CANCELLED);

    String reason = defaultReason;
    String details = null;
    if (request != null) {
      if (request.getReason() != null && !request.getReason().isBlank()) {
        reason = request.getReason();
      }
      if (request.getDetails() != null && !request.getDetails().isBlank()) {
        details = request.getDetails();
      }
    }
    order.setCancellationReason(reason);
    order.setCancellationDetails(details);

    // Restore stock that was debited at confirmation. Per decision D26 a PENDING order never
    // debits stock, so we only credit it back when the order had already moved to CONFIRMED or
    // IN_DELIVERY (where confirmOrder ran registerSale). Without this, cancelling a confirmed
    // order silently loses the debited quantity.
    if (previousStatus == OrderStatus.CONFIRMED || previousStatus == OrderStatus.IN_DELIVERY) {
      order
          .getItems()
          .forEach(
              item ->
                  stockMovementService.registerCancelledSale(
                      item.getProduct(), item.getQuantity(), "Pedido cancelado"));
    }

    appendStatusHistory(order, OrderStatus.CANCELLED);
    return true;
  }

  private boolean isCancellableStatus(OrderStatus status) {
    return status == OrderStatus.PENDING
        || status == OrderStatus.CONFIRMED
        || status == OrderStatus.IN_DELIVERY;
  }

  /**
   * Lists the authenticated customer's orders, newest first. {@code status}, {@code page} and
   * {@code size} are optional: without them the full list is returned (backward compatible with
   * the mobile app, which also already sent {@code status} — it was silently ignored before).
   */
  @Transactional(readOnly = true)
  public List<CustomerOrderResponse> getMyOrders(
      Jwt jwt, OrderStatus status, Integer page, Integer size) {
    User user = userService.requireRole(jwt, TypeUser.CUSTOMER, "Apenas consumidores podem visualizar seus pedidos");

    customerRepository
        .findById(user.getId())
        .orElseThrow(() -> new NotFoundException("Dados do consumidor não encontrados"));

    Page<Order> orders =
        status != null
            ? orderRepository.findByCustomerIdAndStatus(
                user.getId(), status, buildPageable(page, size))
            : orderRepository.findByCustomerId(user.getId(), buildPageable(page, size));
    return orders.getContent().stream().map(this::toCustomerOrderResponse).toList();
  }

  /** Lists the authenticated producer's received orders; same optional filtering/paging. */
  @Transactional(readOnly = true)
  public List<OrderResponse> getProducerOrders(
      Jwt jwt, OrderStatus status, Integer page, Integer size) {
    User user = userService.requireRole(jwt, TypeUser.FARMER, "Apenas produtores podem visualizar pedidos recebidos");

    Page<Order> orders =
        status != null
            ? orderRepository.findByFarmerIdAndStatus(
                user.getId(), status, buildPageable(page, size))
            : orderRepository.findByFarmerId(user.getId(), buildPageable(page, size));
    return orders.getContent().stream()
        .map(order -> OrderMapper.toResponse(order, storageService))
        .toList();
  }

  private Pageable buildPageable(Integer page, Integer size) {
    Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
    if (page == null && size == null) {
      // Compatibilidade: sem parâmetros, devolve a lista inteira como antes da paginação.
      return Pageable.unpaged(sort);
    }
    return PageRequest.of(
        page != null ? Math.max(page, 0) : 0,
        size != null && size > 0 ? size : DEFAULT_PAGE_SIZE,
        sort);
  }

  @Transactional(readOnly = true)
  public CustomerOrderResponse getMyOrderById(UUID orderId, Jwt jwt) {
    User user = userService.requireRole(jwt, TypeUser.CUSTOMER, "Apenas consumidores podem visualizar seus pedidos");

    customerRepository
        .findById(user.getId())
        .orElseThrow(() -> new NotFoundException("Dados do consumidor não encontrados"));

    Order order =
        orderRepository
            .findByIdAndCustomerId(orderId, user.getId())
            .orElseThrow(() -> new NotFoundException("Pedido não encontrado para este consumidor"));

    return toCustomerOrderResponse(order);
  }

  @Transactional
  public OrderResponse markOrderAsSeen(UUID orderId, Jwt jwt) {
    Order order =
        loadOrderOwnedByFarmer(orderId, jwt, "Apenas produtores podem marcar pedidos como vistos");

    if (!order.isSeenByFarmer()) {
      order.setSeenByFarmer(true);
      order = orderRepository.saveAndFlush(order);
    }

    return OrderMapper.toResponse(order, storageService);
  }

  private CustomerOrderResponse toCustomerOrderResponse(Order order) {
    boolean reviewed = reviewRepository.existsByOrderId(order.getId());
    return OrderMapper.toCustomerOrderResponse(order, storageService, reviewed);
  }

  /**
   * Único ponto de entrada genérico de transição pelo produtor. Cada status delega para o fluxo
   * com os efeitos corretos: CONFIRMED debita estoque ({@link #confirmOrder}), CANCELLED devolve
   * estoque e grava motivo ({@link #refuseOrderAsFarmer}), IN_DELIVERY/DELIVERED passam pela
   * máquina de estados. Antes, qualquer transição era aceita sem efeito colateral (achado A3).
   */
  @Transactional
  public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus, Jwt jwt) {
    User user = userService.requireRole(jwt, TypeUser.FARMER, "Apenas produtores podem atualizar o status do pedido");

    return switch (newStatus) {
      case CONFIRMED -> confirmOrder(orderId, jwt);
      case CANCELLED -> refuseOrderAsFarmer(orderId, jwt, null);
      case IN_DELIVERY, DELIVERED -> applyFarmerTransition(orderId, jwt, newStatus);
      case PENDING ->
          throw new BusinessException(
              "Transição de status inválida: um pedido não pode voltar para PENDING");
    };
  }

  private OrderResponse applyFarmerTransition(UUID orderId, Jwt jwt, OrderStatus newStatus) {
    Order order =
        loadOrderOwnedByFarmer(
            orderId, jwt, "Apenas produtores podem atualizar o status do pedido");

    OrderStatus previousStatus = order.getStatus();
    applyTransition(order, newStatus);
    Order updatedOrder = orderRepository.saveAndFlush(order);

    eventPublisher.publishEvent(
        new OrderStatusChangedEvent(updatedOrder, previousStatus, newStatus, TypeUser.FARMER));

    return OrderMapper.toResponse(updatedOrder, storageService);
  }

  @Transactional
  public OrderResponse confirmOrder(UUID orderId, Jwt jwt) {
    Order order =
        loadOrderOwnedByFarmer(orderId, jwt, "Apenas produtores podem confirmar pedidos");

    if (order.getStatus() != OrderStatus.PENDING) {
      throw new BusinessException("Somente pedidos com status PENDING podem ser confirmados");
    }

    order
        .getItems()
        .forEach(
            item ->
                stockMovementService.registerSale(
                    item.getProduct(), item.getQuantity(), "Pedido confirmado"));

    OrderStatus previousStatus = order.getStatus();
    applyTransition(order, OrderStatus.CONFIRMED);
    Order updatedOrder = orderRepository.saveAndFlush(order);

    eventPublisher.publishEvent(
        new OrderStatusChangedEvent(
            updatedOrder, previousStatus, OrderStatus.CONFIRMED, TypeUser.FARMER));

    return OrderMapper.toResponse(updatedOrder, storageService);
  }

  /** Valida contra {@link #VALID_TRANSITIONS}, aplica o status e registra o histórico. */
  private void applyTransition(Order order, OrderStatus newStatus) {
    Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
    if (!allowed.contains(newStatus)) {
      throw new BusinessException(
          "Transição de status inválida: %s → %s".formatted(order.getStatus(), newStatus));
    }
    order.setStatus(newStatus);
    if (newStatus == OrderStatus.IN_DELIVERY) {
      // Gera um código de confirmação novo apenas na transição real para IN_DELIVERY. A máquina de
      // estados só permite CONFIRMED→IN_DELIVERY (nunca reentra), então o reenvio do status não
      // reinicia o código já exibido ao consumidor (regressão corrigida no commit 16714ed).
      order.setConfirmationCode(generateConfirmationCode());
      order.setConfirmationAttempts(0);
      order.setConfirmationLockedUntil(null);
    }
    if (newStatus == OrderStatus.DELIVERED) {
      // Record the delivery time — dashboard metrics filter by deliveredAt.
      order.setDeliveredAt(OffsetDateTime.now());
    }
    appendStatusHistory(order, newStatus);
  }

  /** Registra a entrada de histórico via cascade da coleção (persistida junto com o pedido). */
  private void appendStatusHistory(Order order, OrderStatus status) {
    OrderStatusHistory history = new OrderStatusHistory();
    history.setOrder(order);
    history.setStatus(status);
    order.getStatusHistory().add(history);
  }

  private Order loadOrderOwnedByCustomer(UUID orderId, Jwt jwt, String roleMessage) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException(roleMessage);
    }
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException("Pedido não encontrado"));
    if (!order.getCustomer().getId().equals(user.getId())) {
      throw new ForbiddenException("Você não tem permissão para acessar este pedido");
    }
    return order;
  }

  private Order loadOrderOwnedByFarmer(UUID orderId, Jwt jwt, String roleMessage) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.FARMER) {
      throw new ForbiddenException(roleMessage);
    }
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException("Pedido não encontrado"));
    if (!order.getFarmer().getId().equals(user.getId())) {
      throw new ForbiddenException("Você não tem permissão para acessar este pedido");
    }
    return order;
  }

  private Address getDeliveryAddress(Customer customer) {
    return addressRepository
        .findByUserIdAndIsPrimaryTrue(customer.getId())
        .orElseThrow(
            () -> new BusinessException("Nenhum endereço principal cadastrado para o cliente"));
  }

  private PaymentMethod getPaymentMethod(Producer farmer) {
    PaymentMethod method = findPrimaryPaymentMethod(farmer);
    if (method == null) {
      throw new BusinessException("O produtor não possui nenhum método de pagamento ativo");
    }
    return method;
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
    Order order =
        loadOrderOwnedByCustomer(orderId, jwt, "Apenas consumidores podem repetir pedidos");

    Customer customer =
        customerRepository
            .findById(order.getCustomer().getId())
            .orElseThrow(() -> new NotFoundException("Dados do consumidor não encontrados"));

    Cart cart = cartRepository.findByCustomerIdAndActiveTrue(customer.getId()).orElse(null);

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

        Optional<CartItem> existingItemOpt =
            cart.getItems().stream()
                .filter(
                    item -> item.isActive() && item.getProduct().getId().equals(product.getId()))
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
    return CartMapper.toResponse(
        savedCart, findPrimaryPaymentMethod(savedCart.getFarmer()), storageService);
  }

  private PaymentMethod findPrimaryPaymentMethod(Producer farmer) {
    if (farmer == null) {
      return null;
    }
    List<PaymentMethod> methods =
        paymentMethodRepository.findByFarmerIdAndActiveTrueOrderByCreatedAtAsc(farmer.getId());
    return methods.isEmpty() ? null : methods.get(0);
  }
}
