package br.com.ragro.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.SearchResultResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.SearchService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class SearchControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SearchService searchService;
  @MockBean private UserRepository userRepository;

  @Test
  void shouldReturn200AndUnifiedResults_whenCustomerSearchesMarketplace() throws Exception {
    String sub = "customer-sub";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomer(sub, true)));

    List<SearchResultResponse> response =
        List.of(
            SearchResultResponse.builder()
                .id(UUID.randomUUID())
                .type("product")
                .name("Tomate Cereja")
                .subtitle("Sítio Boa Colheita")
                .producerId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .farmerId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .price(BigDecimal.valueOf(12.90))
                .category("Horta")
                .unit("kg")
                .build(),
            SearchResultResponse.builder()
                .id(UUID.randomUUID())
                .type("producer")
                .name("Sítio Boa Colheita")
                .subtitle("Mariana Alves")
                .producerId(UUID.fromString("660e8400-e29b-41d4-a716-446655440000"))
                .farmerId(UUID.fromString("660e8400-e29b-41d4-a716-446655440000"))
                .rating(BigDecimal.valueOf(4.8))
                .reviewCount(24)
                .build());

    when(searchService.search(org.mockito.ArgumentMatchers.any())).thenReturn(response);

    mockMvc
        .perform(
            get("/search")
                .param("query", "tomate")
                .param("category", "Horta")
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("product"))
        .andExpect(jsonPath("$[0].name").value("Tomate Cereja"))
        .andExpect(jsonPath("$[0].producer_id").value("550e8400-e29b-41d4-a716-446655440000"))
        .andExpect(jsonPath("$[0].farmer_id").value("550e8400-e29b-41d4-a716-446655440000"))
        .andExpect(jsonPath("$[0].category").value("Horta"))
        .andExpect(jsonPath("$[1].type").value("producer"))
        .andExpect(jsonPath("$[1].producer_id").value("660e8400-e29b-41d4-a716-446655440000"))
        .andExpect(jsonPath("$[1].farmer_id").value("660e8400-e29b-41d4-a716-446655440000"))
        .andExpect(jsonPath("$[1].review_count").value(24));
  }

  @Test
  void shouldReturn400_whenQueryIsMissing() throws Exception {
    String sub = "customer-sub";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomer(sub, true)));

    mockMvc
        .perform(
            get("/search")
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn403_whenRoleIsNotCustomer() throws Exception {
    String sub = "farmer-sub";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomer(sub, true)));

    mockMvc
        .perform(
            get("/search")
                .param("query", "tomate")
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isForbidden());
  }

  private User buildCustomer(String sub, boolean active) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setAuthSub(sub);
    user.setName("Cliente");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(active);
    return user;
  }
}
