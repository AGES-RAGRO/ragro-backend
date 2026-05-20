package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.RecommendationProductResponse;
import br.com.ragro.controller.response.RecommendationResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.RecommendationReason;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.RecommendationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(RecommendationController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        User activeCustomer = new User();
        activeCustomer.setId(UUID.randomUUID());
        activeCustomer.setActive(true);
        activeCustomer.setType(TypeUser.CUSTOMER);
        when(userRepository.findByAuthSub(any())).thenReturn(Optional.of(activeCustomer));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getRecommendations_shouldReturn200_whenAuthenticatedAsCustomer() throws Exception {
        var product = RecommendationProductResponse.builder()
                .id(UUID.randomUUID())
                .name("Tomate Orgânico")
                .price(new BigDecimal("12.90"))
                .unityType("kg")
                .farmerId(UUID.randomUUID())
                .farmName("Sítio Boa Vista")
                .categoryNames(List.of("Hortaliças"))
                .score(5)
                .reason(RecommendationReason.FRESHNESS)
                .build();

        var response = RecommendationResponse.builder()
                .recommendations(List.of(product))
                .total(1)
                .build();

        when(recommendationService.getRecommendations(any(), any())).thenReturn(response);

        mockMvc.perform(get("/recommendations?limit=10").with(asCustomer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.recommendations[0].name").value("Tomate Orgânico"))
                .andExpect(jsonPath("$.recommendations[0].score").value(5))
                .andExpect(jsonPath("$.recommendations[0].reason").value("FRESHNESS"));
    }

    @Test
    void getRecommendations_shouldReturn200_whenNoRecommendations() throws Exception {
        var response = RecommendationResponse.builder()
                .recommendations(List.of())
                .total(0)
                .build();

        when(recommendationService.getRecommendations(any(), any())).thenReturn(response);

        mockMvc.perform(get("/recommendations").with(asCustomer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    @Test
    void getRecommendations_shouldReturn403_whenAuthenticatedWithoutCustomerRole() throws Exception {
        mockMvc.perform(get("/recommendations").with(asFarmer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRecommendations_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRecommendations_shouldReturn400_whenLimitIsBelowMinimum() throws Exception {
        mockMvc.perform(get("/recommendations?limit=0").with(asCustomer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRecommendations_shouldReturn400_whenLimitExceedsMaximum() throws Exception {
        mockMvc.perform(get("/recommendations?limit=101").with(asCustomer()))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static RequestPostProcessor asCustomer() {
        return Objects.requireNonNull(
                jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private static RequestPostProcessor asFarmer() {
        return Objects.requireNonNull(
                jwt().authorities(new SimpleGrantedAuthority("ROLE_FARMER")));
    }
}