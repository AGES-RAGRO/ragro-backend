package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.StockExitRequest;
import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.StockMovement;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.StockMovementReason;
import br.com.ragro.domain.enums.StockMovementType;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.ProductRepository;
import br.com.ragro.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

  @Mock private UserService userService;
  @Mock private ProducerRepository producerRepository;
  @Mock private ProductRepository productRepository;
  @Mock private StockMovementRepository stockMovementRepository;

  @InjectMocks private StockMovementService stockMovementService;

  // ─── registerExit — happy paths ─────────────────────────────────────────────

  @Test
  void registerExit_shouldDeductStockAndReturnMovement_withSaleReason() {
    Producer farmer = buildAuthenticatedFarmer();
    Product product = buildProduct(farmer, new BigDecimal("10.000"));
    StockExitRequest request = buildRequest(product.getId(), new BigDecimal("3.000"), StockMovementReason.SALE);

    when(productRepository.findByIdAndFarmerId(product.getId(), farmer.getId()))
        .thenReturn(Optional.of(product));
    when(productRepository.saveAndFlush(product)).thenReturn(product);
    when(stockMovementRepository.saveAndFlush(any(StockMovement.class)))
        .thenAnswer(inv -> {
          StockMovement m = inv.getArgument(0);
          m.setId(UUID.randomUUID());
          return m;
        });

    StockMovementResponse response = stockMovementService.registerExit(request, jwt());

    assertThat(response.getType()).isEqualTo(StockMovementType.EXIT);
    assertThat(response.getReason()).isEqualTo(StockMovementReason.SALE);
    assertThat(response.getQuantity()).isEqualByComparingTo("3.000");
    assertThat(response.getCurrentStockQuantity()).isEqualByComparingTo("7.000");
    assertThat(response.getProductId()).isEqualTo(product.getId());
    verify(productRepository).saveAndFlush(product);
    verify(stockMovementRepository).saveAndFlush(any(StockMovement.class));
  }

  @Test
  void registerExit_shouldDeductStockAndReturnMovement_withLossReason() {
    Producer farmer = buildAuthenticatedFarmer();
    Product product = buildProduct(farmer, new BigDecimal("5.000"));
    StockExitRequest request = buildRequest(product.getId(), new BigDecimal("2.000"), StockMovementReason.LOSS);
    request.setNotes("Damaged goods");

    when(productRepository.findByIdAndFarmerId(product.getId(), farmer.getId()))
        .thenReturn(Optional.of(product));
    when(productRepository.saveAndFlush(product)).thenReturn(product);
    when(stockMovementRepository.saveAndFlush(any(StockMovement.class)))
        .thenAnswer(inv -> {
          StockMovement m = inv.getArgument(0);
          m.setId(UUID.randomUUID());
          return m;
        });

    StockMovementResponse response = stockMovementService.registerExit(request, jwt());

    assertThat(response.getReason()).isEqualTo(StockMovementReason.LOSS);
    assertThat(response.getNotes()).isEqualTo("Damaged goods");
    assertThat(response.getCurrentStockQuantity()).isEqualByComparingTo("3.000");
  }

  @Test
  void registerExit_shouldDeductStockAndReturnMovement_withDisposalReason() {
    Producer farmer = buildAuthenticatedFarmer();
    Product product = buildProduct(farmer, new BigDecimal("8.000"));
    StockExitRequest request = buildRequest(product.getId(), new BigDecimal("1.500"), StockMovementReason.DISPOSAL);

    when(productRepository.findByIdAndFarmerId(product.getId(), farmer.getId()))
        .thenReturn(Optional.of(product));
    when(productRepository.saveAndFlush(product)).thenReturn(product);
    when(stockMovementRepository.saveAndFlush(any(StockMovement.class)))
        .thenAnswer(inv -> {
          StockMovement m = inv.getArgument(0);
          m.setId(UUID.randomUUID());
          return m;
        });

    StockMovementResponse response = stockMovementService.registerExit(request, jwt());

    assertThat(response.getReason()).isEqualTo(StockMovementReason.DISPOSAL);
    assertThat(response.getCurrentStockQuantity()).isEqualByComparingTo("6.500");
  }

  @Test
  void registerExit_shouldAllowExitWhenQuantityEqualsCurrentStock() {
    Producer farmer = buildAuthenticatedFarmer();
    Product product = buildProduct(farmer, new BigDecimal("5.000"));
    StockExitRequest request = buildRequest(product.getId(), new BigDecimal("5.000"), StockMovementReason.SALE);

    when(productRepository.findByIdAndFarmerId(product.getId(), farmer.getId()))
        .thenReturn(Optional.of(product));
    when(productRepository.saveAndFlush(product)).thenReturn(product);
    when(stockMovementRepository.saveAndFlush(any(StockMovement.class)))
        .thenAnswer(inv -> {
          StockMovement m = inv.getArgument(0);
          m.setId(UUID.randomUUID());
          return m;
        });

    StockMovementResponse response = stockMovementService.registerExit(request, jwt());

    assertThat(response.getCurrentStockQuantity()).isEqualByComparingTo("0.000");
  }

  // ─── registerExit — business rule violations ────────────────────────────────

  @Test
  void registerExit_shouldThrowBusinessException_whenStockIsInsufficient() {
    Producer farmer = buildAuthenticatedFarmer();
    Product product = buildProduct(farmer, new BigDecimal("5.000"));
    StockExitRequest request = buildRequest(product.getId(), new BigDecimal("10.000"), StockMovementReason.SALE);

    when(productRepository.findByIdAndFarmerId(product.getId(), farmer.getId()))
        .thenReturn(Optional.of(product));

    assertThatThrownBy(() -> stockMovementService.registerExit(request, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Saldo insuficiente para registrar saída de estoque");

    verify(productRepository, never()).saveAndFlush(any());
    verify(stockMovementRepository, never()).saveAndFlush(any());
  }

  @Test
  void registerExit_shouldThrowBusinessException_whenStockIsZero() {
    Producer farmer = buildAuthenticatedFarmer();
    Product product = buildProduct(farmer, BigDecimal.ZERO);
    StockExitRequest request = buildRequest(product.getId(), new BigDecimal("1.000"), StockMovementReason.SALE);

    when(productRepository.findByIdAndFarmerId(product.getId(), farmer.getId()))
        .thenReturn(Optional.of(product));

    assertThatThrownBy(() -> stockMovementService.registerExit(request, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Saldo insuficiente para registrar saída de estoque");

    verify(productRepository, never()).saveAndFlush(any());
    verify(stockMovementRepository, never()).saveAndFlush(any());
  }

  @Test
  void registerExit_shouldThrowBusinessException_whenReasonIsManualEntry() {
    Producer farmer = buildAuthenticatedFarmer();
    Product product = buildProduct(farmer, new BigDecimal("10.000"));
    StockExitRequest request = buildRequest(product.getId(), new BigDecimal("2.000"), StockMovementReason.MANUAL_ENTRY);

    when(productRepository.findByIdAndFarmerId(product.getId(), farmer.getId()))
        .thenReturn(Optional.of(product));

    assertThatThrownBy(() -> stockMovementService.registerExit(request, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Motivo inválido para saída de estoque");

    verify(productRepository, never()).saveAndFlush(any());
    verify(stockMovementRepository, never()).saveAndFlush(any());
  }

  // ─── registerExit — not found / ownership ───────────────────────────────────

  @Test
  void registerExit_shouldThrowNotFoundException_whenProductDoesNotBelongToFarmer() {
    Producer farmer = buildAuthenticatedFarmer();
    UUID unknownProductId = UUID.randomUUID();
    StockExitRequest request = buildRequest(unknownProductId, new BigDecimal("2.000"), StockMovementReason.SALE);

    when(productRepository.findByIdAndFarmerId(unknownProductId, farmer.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> stockMovementService.registerExit(request, jwt()))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produto não encontrado");

    verify(productRepository, never()).saveAndFlush(any());
    verify(stockMovementRepository, never()).saveAndFlush(any());
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

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
    when(producerRepository.findById(farmerId)).thenReturn(Optional.of(farmer));
    return farmer;
  }

  private Product buildProduct(Producer farmer, BigDecimal stockQuantity) {
    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setFarmer(farmer);
    product.setName("Organic strawberries");
    product.setPrice(new BigDecimal("18.90"));
    product.setUnityType("kg");
    product.setStockQuantity(stockQuantity);
    product.setActive(true);
    return product;
  }

  private StockExitRequest buildRequest(UUID productId, BigDecimal quantity, StockMovementReason reason) {
    StockExitRequest request = new StockExitRequest();
    request.setProductId(productId);
    request.setQuantity(quantity);
    request.setReason(reason);
    return request;
  }

  private Jwt jwt() {
    return new Jwt(
        "token",
        Instant.now(),
        Instant.now().plusSeconds(300),
        Map.of("alg", "none"),
        Map.of("sub", "farmer-sub", "email", "farmer@example.com"));
  }
}
