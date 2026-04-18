package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.ragro.controller.request.AddToCartRequest;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.domain.*;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
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

    productB = new Product();
    productB.setId(UUID.randomUUID());
    productB.setName("Product B");
    productB.setPrice(new BigDecimal("20.00"));
    productB.setFarmer(farmerB);
    productB.setActive(true);
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

  private Jwt jwt() {
    return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300), 
        Map.of("alg", "none"), Map.of("sub", "sub"));
  }
}
