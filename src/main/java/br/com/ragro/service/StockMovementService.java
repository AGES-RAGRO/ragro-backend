package br.com.ragro.service;

import br.com.ragro.controller.request.StockMovementFilter;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.domain.StockMovement;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.domain.specification.StockMovementSpecification;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.StockMovementRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockMovementService {

  private final StockMovementRepository stockMovementRepository;
  private final ProducerRepository producerRepository;
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
}