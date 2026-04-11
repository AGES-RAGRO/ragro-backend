package br.com.ragro.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.ProductResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.ProductService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class ProductControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private ProductService productService;

  @MockBean private UserRepository userRepository;

  @Test
  void getMyProducts_shouldReturn200WithProducts() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    UUID productId = UUID.randomUUID();
    UUID farmerId = user.getId();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(productService.getMyProducts(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(productResponse(productId, farmerId, true)));

    mockMvc
        .perform(get("/farmer/products").with(farmerJwt(sub)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(productId.toString()))
        .andExpect(jsonPath("$[0].name").value("Organic strawberries"));
  }

  @Test
  void createProduct_shouldReturn201WithLocation() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    UUID productId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(productService.createProduct(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(productResponse(productId, user.getId(), true));

    mockMvc
        .perform(
            post("/farmer/products")
                .with(farmerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/farmer/products/" + productId))
        .andExpect(jsonPath("$.id").value(productId.toString()));
  }

  @Test
  void updateProduct_shouldReturn200WithProduct() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    UUID productId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(productService.updateProduct(
            org.mockito.ArgumentMatchers.eq(productId),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(productResponse(productId, user.getId(), true));

    mockMvc
        .perform(
            put("/farmer/products/{id}", productId)
                .with(farmerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(productId.toString()));
  }

  @Test
  void deactivateProduct_shouldReturn200WithInactiveProduct() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    UUID productId = UUID.randomUUID();
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(productService.deactivateProduct(
            org.mockito.ArgumentMatchers.eq(productId), org.mockito.ArgumentMatchers.any()))
        .thenReturn(productResponse(productId, user.getId(), false));

    mockMvc
        .perform(delete("/farmer/products/{id}", productId).with(farmerJwt(sub)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void createProduct_shouldReturn400_whenRequestIsInvalid() throws Exception {
    String sub = "active-farmer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            post("/farmer/products")
                .with(farmerJwt(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getMyProducts_shouldReturn403_whenTokenHasWrongRole() throws Exception {
    String sub = "active-customer";
    User user = buildUser(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));

    mockMvc
        .perform(
            get("/farmer/products")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
  }

  private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor farmerJwt(String sub) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"));
  }

  private ProductResponse productResponse(UUID productId, UUID farmerId, boolean active) {
    return ProductResponse.builder()
        .id(productId)
        .farmerId(farmerId)
        .name("Organic strawberries")
        .price(new BigDecimal("18.90"))
        .unityType("kg")
        .stockQuantity(new BigDecimal("35.500"))
        .active(active)
        .createdAt(OffsetDateTime.now().minusDays(1))
        .updatedAt(OffsetDateTime.now())
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

  private String productJson() {
    return """
        {
          "name": "Organic strawberries",
          "description": "Freshly harvested strawberries.",
          "price": 18.90,
          "unityType": "kg",
          "stockQuantity": 35.500,
          "imageS3": "s3://bucket/products/strawberries.jpg",
          "active": true,
          "categoryIds": [1],
          "photos": [
            {
              "url": "s3://bucket/products/strawberries-1.jpg",
              "displayOrder": 0
            }
          ]
        }
        """;
  }
}
