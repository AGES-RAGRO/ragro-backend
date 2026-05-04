package br.com.ragro.service;

import br.com.ragro.controller.request.StockExitRequest;
import br.com.ragro.controller.request.StockEntryRequest;
import br.com.ragro.controller.request.StockMovementFilter;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.StockMovement;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.StockMovementReason;
import br.com.ragro.domain.enums.StockMovementType;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.domain.specification.StockMovementSpecification;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.mapper.StockMovementMapper;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.ProductRepository;
import br.com.ragro.repository.StockMovementRepository;
import java.util.UUID;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockMovementService {

  private static final java.util.Set<StockMovementReason> EXIT_REASONS =
      java.util.Set.of(StockMovementReason.SALE, StockMovementReason.LOSS, StockMovementReason.DISPOSAL);

  private final StockMovementRepository stockMovementRepository;
  private final ProducerRepository producerRepository;
  private final ProductRepository productRepository;
  private final UserService userService;

  @Transactional(readOnly = true)
  public PaginatedResponse<StockMovementResponse> getProducerStockMovements(
      Jwt jwt, StockMovementFilter filter, Pageable pageable) {

    User authenticated = userService.getAuthenticatedUser(jwt);

    if (authenticated.getType() != TypeUser.FARMER) {
      throw new ForbiddenException("Acesso restrito a produtores");
    }

    UUID producerId = authenticated.getId();

    producerRepository
        .findById(producerId)
        .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    Page<StockMovementResponse> page =
        stockMovementRepository
            .findAll(StockMovementSpecification.withFilter(producerId, filter), pageable)
            .map(this::toResponse);

    return PaginatedResponse.of(page);
  }

  @Transactional
  public StockMovementResponse registerExit(StockExitRequest request, Jwt jwt) {
    Producer farmer = getAuthenticatedFarmer(jwt);
    Product product = getProductOwnedByFarmer(request.getProductId(), farmer.getId());

    if (!EXIT_REASONS.contains(request.getReason())) {
      throw new BusinessException("Motivo inválido para saída de estoque");
    }

    if (product.getStockQuantity().compareTo(request.getQuantity()) < 0) {
      throw new BusinessException("Saldo insuficiente para registrar saída de estoque");
    }

    product.setStockQuantity(product.getStockQuantity().subtract(request.getQuantity()));
    productRepository.saveAndFlush(product);

    StockMovement movement = new StockMovement();
    movement.setProduct(product);
    movement.setType(StockMovementType.EXIT);
    movement.setReason(request.getReason());
    movement.setQuantity(request.getQuantity());
    movement.setNotes(request.getNotes());
    stockMovementRepository.saveAndFlush(movement);

    return StockMovementMapper.toResponse(movement);
  }

  @Transactional
  public StockMovementResponse registerEntry(StockEntryRequest request, Jwt jwt) {
    Producer farmer = getAuthenticatedFarmer(jwt);
    Product product = getProductOwnedByFarmer(request.getProductId(), farmer.getId());

    product.setStockQuantity(product.getStockQuantity().add(request.getQuantity()));
    productRepository.saveAndFlush(product);

    StockMovement movement = new StockMovement();
    movement.setProduct(product);
    movement.setType(StockMovementType.ENTRY);
    movement.setReason(StockMovementReason.MANUAL_ENTRY);
    movement.setQuantity(request.getQuantity());
    movement.setNotes(request.getNotes());
    stockMovementRepository.saveAndFlush(movement);

    return StockMovementMapper.toResponse(movement);
  }

  @Transactional
  public void registerSale(Product product, BigDecimal quantity, String orderIdNotes) {
    if (product.getStockQuantity().compareTo(quantity) < 0) {
      throw new BusinessException("Saldo insuficiente no estoque para o produto " + product.getName());
    }

    product.setStockQuantity(product.getStockQuantity().subtract(quantity));
    productRepository.saveAndFlush(product);

    StockMovement movement = new StockMovement();
    movement.setProduct(product);
    movement.setType(StockMovementType.EXIT);
    movement.setReason(StockMovementReason.SALE);
    movement.setQuantity(quantity);
    movement.setNotes(orderIdNotes);
    stockMovementRepository.saveAndFlush(movement);
  }

  @Transactional
  public void registerCancelledSale(Product product, BigDecimal quantity, String notes) {
    product.setStockQuantity(product.getStockQuantity().add(quantity));
    productRepository.saveAndFlush(product);

    StockMovement movement = new StockMovement();
    movement.setProduct(product);
    movement.setType(StockMovementType.ENTRY);
    movement.setReason(StockMovementReason.CANCELED_SALE);
    movement.setQuantity(quantity);
    movement.setNotes(notes);
    stockMovementRepository.saveAndFlush(movement);
  }

  private StockMovementResponse toResponse(StockMovement movement) {
    return StockMovementResponse.builder()
        .id(movement.getId())
        .productId(movement.getProduct().getId())
        .productName(movement.getProduct().getName())
        .type(movement.getType())
        .reason(movement.getReason())
        .quantity(movement.getQuantity())
        .notes(movement.getNotes())
        .createdAt(movement.getCreatedAt())
        .build();
  }

  private Producer getAuthenticatedFarmer(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.FARMER) {
      throw new UnauthorizedException("Access restricted to farmers");
    }
    return producerRepository
        .findById(user.getId())
        .orElseThrow(() -> new NotFoundException("Dados do produtor não encontrados"));
  }

  private Product getProductOwnedByFarmer(UUID productId, UUID farmerId) {
    return productRepository
        .findByIdAndFarmerId(productId, farmerId)
        .orElseThrow(() -> new NotFoundException("Produto não encontrado"));
  }
}
