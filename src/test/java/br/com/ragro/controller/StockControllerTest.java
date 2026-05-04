package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.StockMovementResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.StockMovementReason;
import br.com.ragro.domain.enums.StockMovementType;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.StockMovementService;
import br.com.ragro.service.StockService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class StockControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private StockMovementService stockMovementService;
  @MockBean private StockService stockService;
  @MockBean private UserRepository userRepository;

  @Test
  void getProductMovements_shouldReturn200WithPaginatedMovements() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    UUID productId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    Page<StockMovementResponse> page =
        new PageImpl<>(
            List.of(
                stockMovementResponse(
                    productId,
                    StockMovementType.ENTRY,
                    StockMovementReason.MANUAL_ENTRY,
                    "Initial entry")),
            PageRequest.of(0, 10),
            1);
    when(stockService.getProductMovements(eq(productId), eq(0), eq(10), any())).thenReturn(page);

    mockMvc
        .perform(
            get("/producers/stock/{productId}/movements", productId)
                .with(farmerJwt(sub)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].productId").value(productId.toString()))
        .andExpect(jsonPath("$.content[0].notes").value("Initial entry"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void getStockMovements_shouldReturn200WithPaginatedMovements() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    UUID productId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    Page<StockMovementResponse> page =
        new PageImpl<>(
            List.of(
                stockMovementResponse(
                    productId,
                    StockMovementType.ENTRY,
                    StockMovementReason.MANUAL_ENTRY,
                    "Manual entry")),
            PageRequest.of(0, 20),
            1);
    when(stockMovementService.getProducerStockMovements(any(), any(), eq(PageRequest.of(0, 20))))
        .thenReturn(PaginatedResponse.of(page));

    mockMvc
        .perform(
            get("/producers/stock/movements")
                .with(farmerJwt(sub))
                .param("productId", productId.toString())
                .param("reason", "MANUAL_ENTRY")
                .param("type", "ENTRY")
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].reason").value("MANUAL_ENTRY"))
        .andExpect(jsonPath("$.content[0].type").value("ENTRY"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void registerStockEntry_shouldReturn201WithMovement() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    UUID productId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(stockMovementService.registerEntry(any(), any()))
        .thenReturn(
            stockMovementResponse(
                productId,
                StockMovementType.ENTRY,
                StockMovementReason.MANUAL_ENTRY,
                "Restock entry"));

    mockMvc
        .perform(
            post("/producers/stock/entry")
                .with(farmerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content(stockEntryJson(productId, "5.000", "Restock entry")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("ENTRY"))
        .andExpect(jsonPath("$.reason").value("MANUAL_ENTRY"))
        .andExpect(jsonPath("$.productId").value(productId.toString()));
  }

  @Test
  void registerStockExit_shouldReturn201WithMovement() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    UUID productId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(stockMovementService.registerExit(any(), any()))
        .thenReturn(
            stockMovementResponse(
                productId,
                StockMovementType.EXIT,
                StockMovementReason.SALE,
                "Sale exit"));

    mockMvc
        .perform(
            post("/producers/stock/exit")
                .with(farmerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                        .content(stockExitJson(productId, "2.000", "SALE")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("EXIT"))
        .andExpect(jsonPath("$.reason").value("SALE"))
        .andExpect(jsonPath("$.productId").value(productId.toString()));
  }

  @Test
  void registerStockEntry_shouldReturn400_whenQuantityIsZero() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            post("/producers/stock/entry")
                .with(farmerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content(stockEntryJson(UUID.randomUUID(), "0.000", "Restock entry")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getStockMovements_shouldReturn403_whenRoleIsCustomer() throws Exception {
    String sub = "active-customer";
    User user = buildCustomerUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            get("/producers/stock/movements")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void registerStockEntry_shouldReturn404_whenProductNotFound() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    UUID productId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(stockMovementService.registerEntry(any(), any()))
        .thenThrow(new NotFoundException("Produto não encontrado"));

    mockMvc
        .perform(
            post("/producers/stock/entry")
                .with(farmerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content(stockEntryJson(productId, "1.000", "Restock entry")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Produto não encontrado"));
  }

  private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor farmerJwt(String sub) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"));
  }

  private StockMovementResponse stockMovementResponse(
      UUID productId, StockMovementType type, StockMovementReason reason, String notes) {
    return StockMovementResponse.builder()
        .id(UUID.randomUUID())
        .productId(productId)
        .productName("Organic strawberries")
        .type(type)
        .reason(reason)
        .quantity(new BigDecimal("2.000"))
        .notes(notes)
        .createdAt(OffsetDateTime.now())
        .currentStockQuantity(new BigDecimal("12.000"))
        .build();
  }

  private User buildUser(String authSub) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test Farmer");
    user.setEmail("farmer@test.com");
    user.setType(TypeUser.FARMER);
    user.setActive(true);
    user.setAuthSub(authSub);
    return user;
  }

  private User buildCustomerUser(String authSub) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test Customer");
    user.setEmail("customer@test.com");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(true);
    user.setAuthSub(authSub);
    return user;
  }

  private String stockEntryJson(UUID productId, String quantity, String notes) {
    return """
        {
          "productId": "%s",
          "quantity": %s,
          "notes": "%s"
        }
        """.formatted(productId, quantity, notes);
  }

  private String stockExitJson(UUID productId, String quantity, String reason) {
    return """
        {
          "productId": "%s",
          "quantity": %s,
          "reason": "%s"
        }
        """.formatted(productId, quantity, reason);
  }
}



