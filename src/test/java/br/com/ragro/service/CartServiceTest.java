package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.ragro.controller.request.AddToCartRequest;
import br.com.ragro.controller.request.UpdateCartItemRequest;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.domain.*;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.CartItemRepository;
import br.com.ragro.repository.CartRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
class CartServiceTest {

  @Mock private UserService userService;
  @Mock private CustomerRepository customerRepository;
  @Mock private ProductRepository productRepository;
  @Mock private CartRepository cartRepository;
  @Mock private CartItemRepository cartItemRepository;

  @InjectMocks private CartService cartService;

  private User user;
  private Customer customer;
  private Producer farmerA;
  private Producer farmerB;
  private Product productA;
  private Product productB;

  @BeforeEach
  void setUp() {
    UUID customerId = UUID.randomUUID();
    user = new User();
    user.setId(customerId);
    user.setType(TypeUser.CUSTOMER);

    customer = new Customer();
    customer.setId(customerId);
    customer.setUser(user);

    farmerA = new Producer();
    farmerA.setId(UUID.randomUUID());
    farmerA.setFarmName("Farmer A");

    farmerB = new Producer();
    farmerB.setId(UUID.randomUUID());
    farmerB.setFarmName("Farmer B");

    productA = new Product();
    productA.setId(UUID.randomUUID());
    productA.setName("Product A");
    productA.setPrice(new BigDecimal("10.00"));
    productA.setFarmer(farmerA);
    productA.setActive(true);
    productA.setStockQuantity(new BigDecimal("10.000"));

    productB = new Product();
    productB.setId(UUID.randomUUID());
    productB.setName("Product B");
    productB.setPrice(new BigDecimal("20.00"));
    productB.setFarmer(farmerB);
    productB.setActive(true);
    productB.setStockQuantity(new BigDecimal("10.000"));
  }

  @Test
  void addItem_shouldCreateCartAutomaticallyOnFirstItem() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(productRepository.findById(productA.getId())).thenReturn(Optional.of(productA));
    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.empty());
    
    when(cartRepository.save(any(Cart.class))).thenAnswer(i -> {
        Cart c = i.getArgument(0);
        c.setId(UUID.randomUUID());
        return c;
    });

    when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(i -> i.getArgument(0));

    AddToCartRequest req = new AddToCartRequest();
    req.setProductId(productA.getId());
    req.setQuantity(new BigDecimal("2"));

    CartResponse response = cartService.addItem(jwt(), req);

    assertThat(response.getFarmerId()).isEqualTo(farmerA.getId());
    assertThat(response.getItems()).hasSize(1);
    verify(cartRepository, times(1)).save(any(Cart.class));
    verify(cartRepository, times(1)).saveAndFlush(any(Cart.class));
  }

  @Test
  void addItem_shouldBlockDifferentFarmer() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
    when(productRepository.findById(productB.getId())).thenReturn(Optional.of(productB));

    Cart existingCart = new Cart();
    existingCart.setId(UUID.randomUUID());
    existingCart.setFarmer(farmerA);
    existingCart.setCustomer(customer);
    
    CartItem activeItem = new CartItem();
    activeItem.setProduct(productA);
    activeItem.setActive(true);
    existingCart.getItems().add(activeItem);

    when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(existingCart));

    AddToCartRequest req = new AddToCartRequest();
    req.setProductId(productB.getId());
    req.setQuantity(new BigDecimal("1"));

    assertThatThrownBy(() -> cartService.addItem(jwt(), req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("já possui itens de outro produtor");
  }

  @Test
  void addItem_shouldAllowFarmerChangeIfCartIsEmpty() {
      when(userService.getAuthenticatedUser(any())).thenReturn(user);
      when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
      when(productRepository.findById(productB.getId())).thenReturn(Optional.of(productB));

      Cart existingCart = new Cart();
      existingCart.setId(UUID.randomUUID());
      existingCart.setFarmer(farmerA);
      existingCart.setCustomer(customer);
      existingCart.setItems(new ArrayList<>()); // Empty

      when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(existingCart));
      when(cartRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

      AddToCartRequest req = new AddToCartRequest();
      req.setProductId(productB.getId());
      req.setQuantity(new BigDecimal("1"));

      CartResponse response = cartService.addItem(jwt(), req);

      assertThat(response.getFarmerId()).isEqualTo(farmerB.getId());
  }

  @Test
  void addItem_shouldIncrementQuantity_whenItemAlreadyInCart() {
      when(userService.getAuthenticatedUser(any())).thenReturn(user);
      when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
      when(productRepository.findById(productA.getId())).thenReturn(Optional.of(productA));

      Cart existingCart = new Cart();
      existingCart.setId(UUID.randomUUID());
      existingCart.setFarmer(farmerA);
      existingCart.setCustomer(customer);

      CartItem existingItem = new CartItem();
      existingItem.setId(UUID.randomUUID());
      existingItem.setCart(existingCart);
      existingItem.setProduct(productA);
      existingItem.setQuantity(new BigDecimal("2"));
      existingItem.setActive(true);
      existingCart.getItems().add(existingItem);

      when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(existingCart));
      when(cartItemRepository.findByCartIdAndProductIdAndActiveTrue(existingCart.getId(), productA.getId()))
          .thenReturn(Optional.of(existingItem));
      when(cartRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

      AddToCartRequest req = new AddToCartRequest();
      req.setProductId(productA.getId());
      req.setQuantity(new BigDecimal("3")); // 2 + 3 = 5

      CartResponse response = cartService.addItem(jwt(), req);

      assertThat(response.getItems().get(0).getQuantity()).isEqualByComparingTo("5");
      verify(cartItemRepository).save(existingItem);
  }

  @Test
  void addItem_shouldThrowException_whenStockIsInsufficient() {
      when(userService.getAuthenticatedUser(any())).thenReturn(user);
      when(customerRepository.findById(user.getId())).thenReturn(Optional.of(customer));
      when(productRepository.findById(productA.getId())).thenReturn(Optional.of(productA));

      Cart existingCart = new Cart();
      existingCart.setId(UUID.randomUUID());
      existingCart.setFarmer(farmerA);

      CartItem existingItem = new CartItem();
      existingItem.setProduct(productA);
      existingItem.setQuantity(new BigDecimal("8")); // Stock is 10
      existingItem.setActive(true);
      existingCart.getItems().add(existingItem);

      when(cartRepository.findByCustomerIdAndActiveTrue(customer.getId())).thenReturn(Optional.of(existingCart));

      AddToCartRequest req = new AddToCartRequest();
      req.setProductId(productA.getId());
      req.setQuantity(new BigDecimal("3")); // 8 + 3 = 11 > 10

      assertThatThrownBy(() -> cartService.addItem(jwt(), req))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("excede o estoque disponível");
  }

  @Test
  void removeItem_shouldKeepCart_whenStillHasActiveItems() {
    UUID itemIdToRemove = UUID.randomUUID();
    UUID remainingItemId = UUID.randomUUID();

    Cart cart = new Cart();
    cart.setId(UUID.randomUUID());
    cart.setCustomer(customer);
    cart.setFarmer(farmerA);
    cart.setItems(new ArrayList<>());

    CartItem itemToRemove = new CartItem();
    itemToRemove.setId(itemIdToRemove);
    itemToRemove.setCart(cart);
    itemToRemove.setProduct(productA);
    itemToRemove.setQuantity(new BigDecimal("1"));
    itemToRemove.setActive(true);

    CartItem remainingItem = new CartItem();
    remainingItem.setId(remainingItemId);
    remainingItem.setCart(cart);
    remainingItem.setProduct(productB);
    remainingItem.setQuantity(new BigDecimal("2"));
    remainingItem.setActive(true);

    cart.getItems().add(itemToRemove);
    cart.getItems().add(remainingItem);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(cartRepository.findByCustomerIdAndActiveTrue(user.getId())).thenReturn(Optional.of(cart));
    when(cartItemRepository.findByCartIdAndIdAndActiveTrue(cart.getId(), itemIdToRemove))
        .thenReturn(Optional.of(itemToRemove));
    when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(i -> i.getArgument(0));

    CartResponse response = cartService.removeItem(jwt(), itemIdToRemove);

    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getItems().get(0).getId()).isEqualTo(remainingItemId);
    verify(cartItemRepository).delete(itemToRemove);
    verify(cartItemRepository, never()).save(itemToRemove);
    verify(cartRepository).saveAndFlush(cart);
    verify(cartRepository, never()).delete(any(Cart.class));
  }

  @Test
  void removeItem_shouldDeleteCart_whenLastActiveItemIsRemoved() {
    UUID itemIdToRemove = UUID.randomUUID();

    Cart cart = new Cart();
    cart.setId(UUID.randomUUID());
    cart.setCustomer(customer);
    cart.setFarmer(farmerA);
    cart.setItems(new ArrayList<>());

    CartItem itemToRemove = new CartItem();
    itemToRemove.setId(itemIdToRemove);
    itemToRemove.setCart(cart);
    itemToRemove.setProduct(productA);
    itemToRemove.setQuantity(new BigDecimal("1"));
    itemToRemove.setActive(true);
    cart.getItems().add(itemToRemove);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(cartRepository.findByCustomerIdAndActiveTrue(user.getId())).thenReturn(Optional.of(cart));
    when(cartItemRepository.findByCartIdAndIdAndActiveTrue(cart.getId(), itemIdToRemove))
        .thenReturn(Optional.of(itemToRemove));

    CartResponse response = cartService.removeItem(jwt(), itemIdToRemove);

    assertThat(response.getItems()).isEmpty();
    verify(cartItemRepository).delete(itemToRemove);
    verify(cartItemRepository, never()).save(itemToRemove);
    verify(cartRepository).delete(cart);
    verify(cartRepository).flush();
    verify(cartRepository, never()).saveAndFlush(any(Cart.class));
  }

  @Test
  void updateItemQuantity_shouldReplaceQuantityAndRecalculateTotal() {
    Cart cart = new Cart();
    cart.setId(UUID.randomUUID());
    cart.setCustomer(customer);
    cart.setFarmer(farmerA);
    cart.setActive(true);

    CartItem item = new CartItem();
    item.setId(UUID.randomUUID());
    item.setCart(cart);
    item.setProduct(productA);
    item.setQuantity(new BigDecimal("2"));
    item.setActive(true);
    cart.getItems().add(item);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(cartItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
    when(cartRepository.saveAndFlush(cart)).thenReturn(cart);

    UpdateCartItemRequest req = new UpdateCartItemRequest();
    req.setQuantity(new BigDecimal("5"));

    CartResponse response = cartService.updateItemQuantity(jwt(), item.getId(), req);

    assertThat(item.getQuantity()).isEqualByComparingTo("5");
    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getItems().get(0).getQuantity()).isEqualByComparingTo("5");
    assertThat(response.getTotalAmount()).isEqualByComparingTo("50.00");
    verify(cartItemRepository).save(item);
  }

  @Test
  void updateItemQuantity_shouldThrowNotFound_whenItemDoesNotExist() {
    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    UUID itemId = UUID.randomUUID();
    when(cartItemRepository.findById(itemId)).thenReturn(Optional.empty());

    UpdateCartItemRequest req = new UpdateCartItemRequest();
    req.setQuantity(new BigDecimal("3"));

    assertThatThrownBy(() -> cartService.updateItemQuantity(jwt(), itemId, req))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Item do carrinho não encontrado");
  }

  @Test
  void updateItemQuantity_shouldThrowForbidden_whenItemBelongsToAnotherCustomer() {
    Customer otherCustomer = new Customer();
    otherCustomer.setId(UUID.randomUUID());

    Cart cart = new Cart();
    cart.setId(UUID.randomUUID());
    cart.setCustomer(otherCustomer);
    cart.setFarmer(farmerA);
    cart.setActive(true);

    CartItem item = new CartItem();
    item.setId(UUID.randomUUID());
    item.setCart(cart);
    item.setProduct(productA);
    item.setQuantity(new BigDecimal("2"));
    item.setActive(true);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(cartItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

    UpdateCartItemRequest req = new UpdateCartItemRequest();
    req.setQuantity(new BigDecimal("3"));

    assertThatThrownBy(() -> cartService.updateItemQuantity(jwt(), item.getId(), req))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("não pertence ao seu carrinho");
  }

  @Test
  void updateItemQuantity_shouldThrowNotFound_whenItemIsInactive() {
    Cart cart = new Cart();
    cart.setId(UUID.randomUUID());
    cart.setCustomer(customer);
    cart.setFarmer(farmerA);
    cart.setActive(true);

    CartItem item = new CartItem();
    item.setId(UUID.randomUUID());
    item.setCart(cart);
    item.setProduct(productA);
    item.setQuantity(new BigDecimal("2"));
    item.setActive(false);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(cartItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

    UpdateCartItemRequest req = new UpdateCartItemRequest();
    req.setQuantity(new BigDecimal("3"));

    assertThatThrownBy(() -> cartService.updateItemQuantity(jwt(), item.getId(), req))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void updateItemQuantity_shouldThrowBusinessException_whenStockIsInsufficient() {
    Cart cart = new Cart();
    cart.setId(UUID.randomUUID());
    cart.setCustomer(customer);
    cart.setFarmer(farmerA);
    cart.setActive(true);

    CartItem item = new CartItem();
    item.setId(UUID.randomUUID());
    item.setCart(cart);
    item.setProduct(productA);
    item.setQuantity(new BigDecimal("2"));
    item.setActive(true);

    when(userService.getAuthenticatedUser(any())).thenReturn(user);
    when(cartItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

    UpdateCartItemRequest req = new UpdateCartItemRequest();
    req.setQuantity(new BigDecimal("15")); // stock is 10

    assertThatThrownBy(() -> cartService.updateItemQuantity(jwt(), item.getId(), req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("excede o estoque disponível");
    verify(cartItemRepository, never()).save(any());
  }

  @Test
  void updateItemQuantity_shouldThrowForbidden_whenUserIsNotCustomer() {
    user.setType(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    UpdateCartItemRequest req = new UpdateCartItemRequest();
    req.setQuantity(new BigDecimal("3"));

    assertThatThrownBy(() -> cartService.updateItemQuantity(jwt(), UUID.randomUUID(), req))
        .isInstanceOf(ForbiddenException.class);
  }

  private Jwt jwt() {
    return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300),
        Map.of("alg", "none"), Map.of("sub", "sub"));
  }
}
