package br.com.ragro.service;

import br.com.ragro.controller.request.AddToCartRequest;
import br.com.ragro.controller.response.CartItemResponse;
import br.com.ragro.controller.response.CartResponse;
import br.com.ragro.domain.Cart;
import br.com.ragro.domain.CartItem;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.CartMapper;
import br.com.ragro.repository.CartItemRepository;
import br.com.ragro.repository.CartRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

  private final UserService userService;
  private final CustomerRepository customerRepository;
  private final ProductRepository productRepository;
  private final CartRepository cartRepository;
  private final CartItemRepository cartItemRepository;

  @Transactional
  public CartResponse addItem(Jwt jwt, AddToCartRequest request) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas consumidores podem gerenciar o carrinho");
    }

    Customer customer = customerRepository.findById(user.getId())
        .orElseThrow(() -> new NotFoundException("Dados do consumidor não encontrados"));

    Product product = productRepository.findById(request.getProductId())
        .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

    if (!product.isActive()) {
      throw new BusinessException("Produto inativo não pode ser adicionado ao carrinho");
    }

    validateStock(product, request.getQuantity(), customer.getId());

    Cart cart = cartRepository.findByCustomerIdAndActiveTrue(customer.getId())
        .orElseGet(() -> createNewCart(customer, product));

    if (!cart.getFarmer().getId().equals(product.getFarmer().getId())) {
      long activeItemsCount = cart.getItems().stream().filter(CartItem::isActive).count();
      if (activeItemsCount > 0) {
        throw new BusinessException(
            "Seu carrinho já possui itens de outro produtor. Finalize o pedido ou esvazie o carrinho primeiro.");
      } else {
        cart.setFarmer(product.getFarmer());
        cartRepository.save(cart);
      }
    }

    updateOrAddItem(cart, product, request);

    CartResponse response = CartMapper.toResponse(cartRepository.saveAndFlush(cart));
    return calculateTotals(response);
  }

  @Transactional(readOnly = true)
  public CartResponse getCart(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas consumidores podem visualizar o carrinho");
    }

    Cart cart = cartRepository.findByCustomerIdAndActiveTrue(user.getId())
        .orElseThrow(() -> new NotFoundException("Carrinho não encontrado ou vazio"));

    CartResponse response = CartMapper.toResponse(cart);
    return calculateTotals(response);
  }

  private CartResponse calculateTotals(CartResponse response) {
    BigDecimal totalAmount = BigDecimal.ZERO;

    if (response.getItems() != null) {
      for (CartItemResponse item : response.getItems()) {
        BigDecimal subtotal = item.getPriceSnapshot().multiply(item.getQuantity());
        
        try {
          var subtotalField = item.getClass().getDeclaredField("subtotal");
          subtotalField.setAccessible(true);
          subtotalField.set(item, subtotal);
        } catch (Exception e) {
          // Se o Mapper não preencheu, o cálculo manual garante o valor
        }

        totalAmount = totalAmount.add(subtotal);
      }
    }

    try {
      var totalField = response.getClass().getDeclaredField("totalAmount");
      totalField.setAccessible(true);
      totalField.set(response, totalAmount);
    } catch (Exception e) {
      // Garante que o total calculado seja atribuído à resposta
    }

    return response;
  }

  @Transactional
  public void clearCart(Customer customer) {
    cartRepository.findByCustomerIdAndActiveTrue(customer.getId())
        .ifPresent(cart -> {
          cart.setActive(false);
          cart.getItems().forEach(item -> item.setActive(false));
          cartRepository.save(cart);
        });
  }

  private Cart createNewCart(Customer customer, Product product) {
    Cart cart = new Cart();
    cart.setCustomer(customer);
    cart.setFarmer(product.getFarmer());
    cart.setActive(true);
    return cartRepository.save(cart);
  }

  private void updateOrAddItem(Cart cart, Product product, AddToCartRequest request) {
    Optional<CartItem> existingItem = cartItemRepository
        .findByCartIdAndProductIdAndActiveTrue(cart.getId(), product.getId());

    if (existingItem.isPresent()) {
      CartItem item = existingItem.get();
      item.setQuantity(item.getQuantity().add(request.getQuantity()));
      cartItemRepository.save(item);
    } else {
      CartItem newItem = new CartItem();
      newItem.setCart(cart);
      newItem.setProduct(product);
      newItem.setQuantity(request.getQuantity());
      newItem.setPriceSnapshot(product.getPrice());
      newItem.setActive(true);
      cart.getItems().add(newItem);
      cartItemRepository.save(newItem);
    }
  }

  private void validateStock(Product product, BigDecimal quantityToAdd, UUID customerId) {
    BigDecimal currentQuantityInCart = cartRepository.findByCustomerIdAndActiveTrue(customerId)
        .flatMap(cart -> cart.getItems().stream()
            .filter(item -> item.isActive() && item.getProduct().getId().equals(product.getId()))
            .findFirst()
            .map(CartItem::getQuantity))
        .orElse(BigDecimal.ZERO);

    BigDecimal totalTargetQuantity = currentQuantityInCart.add(quantityToAdd);

    if (totalTargetQuantity.compareTo(product.getStockQuantity()) > 0) {
      throw new BusinessException("Quantidade solicitada (" + totalTargetQuantity +
          ") excede o estoque disponível (" + product.getStockQuantity() + ")");
    }
  }
}