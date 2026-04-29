package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.CartItemResponse;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.CartService;
import java.math.BigDecimal;
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

@WebMvcTest(CartController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class CartControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private CartService cartService;

  @MockBean private UserRepository userRepository;

  @Test
  void updateItemQuantity_shouldReturn200WithRecalculatedTotal() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    UUID itemId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(cartService.updateItemQuantity(any(), eq(itemId), any()))
        .thenReturn(cartResponse(itemId, new BigDecimal("5"), new BigDecimal("50.00")));

    mockMvc
        .perform(
            patch("/customers/carts/items/{id}", itemId)
                .with(customerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 5}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].quantity").value(5))
        .andExpect(jsonPath("$.totalAmount").value(50.00));
  }

  @Test
  void updateItemQuantity_shouldReturn400_whenQuantityIsMissing() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            patch("/customers/carts/items/{id}", UUID.randomUUID())
                .with(customerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateItemQuantity_shouldReturn400_whenQuantityIsBelowMinimum() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            patch("/customers/carts/items/{id}", UUID.randomUUID())
                .with(customerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 0}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateItemQuantity_shouldReturn404_whenItemDoesNotExist() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    UUID itemId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(cartService.updateItemQuantity(any(), eq(itemId), any()))
        .thenThrow(new NotFoundException("Item do carrinho não encontrado"));

    mockMvc
        .perform(
            patch("/customers/carts/items/{id}", itemId)
                .with(customerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 3}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateItemQuantity_shouldReturn403_whenItemBelongsToAnotherCustomer() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    UUID itemId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(cartService.updateItemQuantity(any(), eq(itemId), any()))
        .thenThrow(new ForbiddenException("Este item não pertence ao seu carrinho"));

    mockMvc
        .perform(
            patch("/customers/carts/items/{id}", itemId)
                .with(customerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 3}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateItemQuantity_shouldReturn400_whenStockIsInsufficient() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    UUID itemId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(cartService.updateItemQuantity(any(), eq(itemId), any()))
        .thenThrow(new BusinessException("Quantidade solicitada (15) excede o estoque disponível (10)"));

    mockMvc
        .perform(
            patch("/customers/carts/items/{id}", itemId)
                .with(customerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 15}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void clearCart_shouldReturn200() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(cartService.clearActiveCart(any()))
        .thenReturn(CartResponse.builder()
            .id(UUID.randomUUID())
            .farmerId(UUID.randomUUID())
            .farmName("Empty Farm")
            .items(List.of())
            .totalAmount(BigDecimal.ZERO)
            .build());

    mockMvc
        .perform(
            delete("/customers/carts")
                .with(customerJwt(sub)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalAmount").value(0));
  }

  @Test
  void updateItemQuantity_shouldReturn403_whenRoleIsNotCustomer() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    user.setType(TypeUser.FARMER);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            patch("/customers/carts/items/{id}", UUID.randomUUID())
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 3}"))
        .andExpect(status().isForbidden());
  }

  private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customerJwt(String sub) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
  }

  private User buildUser(String authSub) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test Customer");
    user.setEmail("customer@test.com");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(true);
    user.setAuthSub(authSub);
    return user;
  }

  private CartResponse cartResponse(UUID itemId, BigDecimal quantity, BigDecimal total) {
    return CartResponse.builder()
        .id(UUID.randomUUID())
        .farmerId(UUID.randomUUID())
        .farmName("Test Farm")
        .items(
            List.of(
                CartItemResponse.builder()
                    .id(itemId)
                    .productId(UUID.randomUUID())
                    .productName("Test Product")
                    .unitPrice(new BigDecimal("10.00"))
                    .quantity(quantity)
                    .subtotal(total)
                    .build()))
        .totalAmount(total)
        .build();
  }
}
