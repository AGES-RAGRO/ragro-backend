package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.StockMovement;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.StockMovementReason;
import br.com.ragro.domain.enums.StockMovementType;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.ProductRepository;
import br.com.ragro.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

  @Mock private UserService userService;

  @Mock private ProductRepository productRepository;

  @Mock private StockMovementRepository stockMovementRepository;

  @InjectMocks private StockService stockService;

  @Test
  void getProductMovements_shouldReturnPaginatedMovementsInDescendingOrder() {
    Producer farmer = buildAuthenticatedFarmer();
    Product product = buildProduct(farmer);

    StockMovement newest = buildMovement(product, "Newest", OffsetDateTime.now());
    StockMovement oldest = buildMovement(product, "Oldest", OffsetDateTime.now().minusDays(1));

    Pageable pageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
    when(productRepository.findByIdAndFarmerId(product.getId(), farmer.getId()))
        .thenReturn(Optional.of(product));
    when(stockMovementRepository.findAllByProductId(product.getId(), pageable))
        .thenReturn(new PageImpl<>(List.of(newest, oldest), pageable, 2));

    Page<StockMovementResponse> response =
        stockService.getProductMovements(product.getId(), 1, 5, jwt());

    assertThat(response.getContent()).hasSize(2);
    assertThat(response.getContent().get(0).getNotes()).isEqualTo("Newest");
    assertThat(response.getContent().get(1).getNotes()).isEqualTo("Oldest");
    assertThat(response.getTotalElements()).isEqualTo(2);
    verify(stockMovementRepository).findAllByProductId(product.getId(), pageable);
  }

  @Test
  void getProductMovements_shouldThrowNotFound_whenProductDoesNotBelongToFarmer() {
    Producer farmer = buildAuthenticatedFarmer();
    UUID productId = UUID.randomUUID();
    when(productRepository.findByIdAndFarmerId(productId, farmer.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> stockService.getProductMovements(productId, 0, 10, jwt()))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produto não encontrado");
  }

  private Producer buildAuthenticatedFarmer() {
    UUID farmerId = UUID.randomUUID();
    User user = new User();
    user.setId(farmerId);
    user.setType(TypeUser.FARMER);
    user.setActive(true);

    Producer farmer = new Producer();
    farmer.setId(farmerId);
    farmer.setUser(user);

    when(userService.getAuthenticatedUser(any(Jwt.class))).thenReturn(user);
    return farmer;
  }

  private Product buildProduct(Producer farmer) {
    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setFarmer(farmer);
    product.setName("Organic strawberries");
    product.setPrice(new BigDecimal("18.90"));
    product.setUnityType("kg");
    product.setStockQuantity(new BigDecimal("35.500"));
    return product;
  }

  private StockMovement buildMovement(Product product, String notes, OffsetDateTime createdAt) {
    StockMovement movement = new StockMovement();
    movement.setId(UUID.randomUUID());
    movement.setProduct(product);
    movement.setType(StockMovementType.ENTRY);
    movement.setReason(StockMovementReason.MANUAL_ENTRY);
    movement.setQuantity(new BigDecimal("12.500"));
    movement.setNotes(notes);
    movement.setCreatedAt(createdAt);
    return movement;
  }

  private Jwt jwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("sub", "sub-123")
        .claim("email", "farmer@test.com")
        .build();
  }
}