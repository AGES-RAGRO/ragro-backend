package br.com.ragro.service;

import br.com.ragro.controller.request.AddToCartRequest;
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

    Cart cart = cartRepository.findByCustomerIdAndActiveTrue(customer.getId())
        .orElseGet(() -> createNewCart(customer, product));

    // Validar se o produto pertence ao mesmo produtor do carrinho
    if (!cart.getFarmer().getId().equals(product.getFarmer().getId())) {
        // Se o carrinho existe mas está vazio (não deveria acontecer se criamos com o farmer do primeiro produto)
        // mas em cenários de reuso de registro ou erro, verificamos se há itens ativos.
        long activeItemsCount = cart.getItems().stream().filter(CartItem::isActive).count();
        if (activeItemsCount > 0) {
            throw new BusinessException("Seu carrinho já possui itens de outro produtor. Finalize o pedido ou esvazie o carrinho primeiro.");
        } else {
            // Se estava vazio, permitimos trocar o produtor
            cart.setFarmer(product.getFarmer());
            cartRepository.save(cart);
        }
    }

    updateOrAddItem(cart, product, request);

    return CartMapper.toResponse(cartRepository.saveAndFlush(cart));
  }

  @Transactional(readOnly = true)
  public CartResponse getCart(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.CUSTOMER) {
      throw new ForbiddenException("Apenas consumidores podem visualizar o carrinho");
    }

    return cartRepository.findByCustomerIdAndActiveTrue(user.getId())
        .map(CartMapper::toResponse)
        .orElseThrow(() -> new NotFoundException("Carrinho não encontrado ou vazio"));
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
      item.setQuantity(request.getQuantity());
      cartItemRepository.save(item);
    } else {
      CartItem newItem = new CartItem();
      newItem.setCart(cart);
      newItem.setProduct(product);
      newItem.setQuantity(request.getQuantity());
      newItem.setActive(true);
      cart.getItems().add(newItem);
      cartItemRepository.save(newItem);
    }
  }
}
