package br.com.ragro.service;

import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.StockMovementMapper;
import br.com.ragro.repository.ProductRepository;
import br.com.ragro.repository.StockMovementRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

  private final UserService userService;
  private final ProductRepository productRepository;
  private final StockMovementRepository stockMovementRepository;

  @Transactional(readOnly = true)
  public Page<StockMovementResponse> getProductMovements(UUID productId, int page, int size, Jwt jwt) {
    Producer farmer = getAuthenticatedFarmer(jwt);
    Product product = getOwnedProduct(productId, farmer.getId());
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

    return stockMovementRepository.findAllByProductId(product.getId(), pageable)
        .map(StockMovementMapper::toResponse);
  }

  private Producer getAuthenticatedFarmer(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.FARMER) {
      throw new NotFoundException("Produto não encontrado");
    }

    Producer farmer = new Producer();
    farmer.setId(user.getId());
    farmer.setUser(user);
    return farmer;
  }

  private Product getOwnedProduct(UUID productId, UUID farmerId) {
    return productRepository.findByIdAndFarmerId(productId, farmerId)
        .orElseThrow(() -> new NotFoundException("Produto não encontrado"));
  }
}