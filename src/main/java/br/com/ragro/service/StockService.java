package br.com.ragro.service;

import br.com.ragro.mapper.StockMovementMapper;
import br.com.ragro.controller.request.StockEntryRequest;
import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.StockMovement;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.StockMovementReason;
import br.com.ragro.domain.enums.StockMovementType;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.ProductRepository;
import br.com.ragro.repository.StockMovementRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {


  private final ProductRepository productRepository;
  private final StockMovementRepository stockMovementRepository;
  private final UserService userService;

  @Transactional
  public StockMovementResponse recordEntry(StockEntryRequest request, Jwt jwt) {
    
    User user = userService.getAuthenticatedUser(jwt);
    UUID farmerId = user.getId(); 

    
    Product product =
        productRepository
            .findByIdAndFarmerId(request.getProductId(), farmerId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Product not found or does not belong to the authenticated farmer"));

    
    product.setStockQuantity(product.getStockQuantity().add(request.getQuantity()));

                    
    StockMovement movement = new StockMovement();
    movement.setProduct(product);
    movement.setType(StockMovementType.ENTRY);
    movement.setReason(StockMovementReason.MANUAL_ENTRY);
    movement.setQuantity(request.getQuantity());
    movement.setNotes(request.getNotes());

    
    productRepository.saveAndFlush(product);
    stockMovementRepository.saveAndFlush(movement);

    
    return StockMovementMapper.toResponse(movement);
  }
}

