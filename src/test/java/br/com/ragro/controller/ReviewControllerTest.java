package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.ProducerReviewsResponse;
import br.com.ragro.controller.response.ReviewItemResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.ProducerService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class ReviewControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private ProducerService producerService;
  @MockBean private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    // Mock UserRepository to return an active user for any authSub
    when(userRepository.findByAuthSub(any())).thenAnswer(invocation -> {
      String authSub = invocation.getArgument(0);
      return Optional.of(buildUser(authSub, true));
    });
  }

  // ─── GET /reviews/producers/{id} ────────────────────────────────────────────

  @Test
  void getProducerReviews_shouldReturn200_withReviewsList_whenProducerExists() throws Exception {
    UUID producerId = UUID.randomUUID();
    
    ProducerReviewsResponse response = buildProducerReviewsResponse(
        producerId,
        new BigDecimal("4.80"),
        3,
        0,
        10,
        3
    );
    
    when(producerService.getProducerReviews(eq(producerId), any(Pageable.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/reviews/producers/" + producerId)
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", "customer-1").claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.averageRating").value(4.80))
        .andExpect(jsonPath("$.totalReviews").value(3))
        .andExpect(jsonPath("$.reviews").isArray())
        .andExpect(jsonPath("$.reviews[0].id").exists())
        .andExpect(jsonPath("$.reviews[0].customerName").value("Maria Cliente"))
        .andExpect(jsonPath("$.reviews[0].rating").value(5))
        .andExpect(jsonPath("$.reviews[0].comment").value("Ótimo produtor!"))
        .andExpect(jsonPath("$.reviews[0].createdAt").exists())
        .andExpect(jsonPath("$.pageNumber").value(0))
        .andExpect(jsonPath("$.pageSize").value(10))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  void getProducerReviews_shouldReturn404_whenProducerNotFound() throws Exception {
    UUID producerId = UUID.randomUUID();
    
    when(producerService.getProducerReviews(eq(producerId), any(Pageable.class)))
        .thenThrow(new NotFoundException("Produtor não encontrado"));

    mockMvc
        .perform(
            get("/reviews/producers/" + producerId)
                .contentType(MediaType.APPLICATION_JSON)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", "customer-1").claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void getProducerReviews_shouldReturn200_withEmptyList_whenProducerHasNoReviews() throws Exception {
    UUID producerId = UUID.randomUUID();
    
    ProducerReviewsResponse response = ProducerReviewsResponse.builder()
        .averageRating(BigDecimal.ZERO)
        .totalReviews(0)
        .reviews(List.of())
        .pageNumber(0)
        .pageSize(10)
        .totalPages(0)
        .totalElements(0L)
        .build();
    
    when(producerService.getProducerReviews(eq(producerId), any(Pageable.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/reviews/producers/" + producerId)
                .contentType(MediaType.APPLICATION_JSON)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", "customer-1").claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.averageRating").value(0))
        .andExpect(jsonPath("$.totalReviews").value(0))
        .andExpect(jsonPath("$.reviews").isArray())
        .andExpect(jsonPath("$.reviews.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void getProducerReviews_shouldRespectPageParameter() throws Exception {
    UUID producerId = UUID.randomUUID();
    
    ProducerReviewsResponse response = buildProducerReviewsResponse(
        producerId,
        new BigDecimal("4.50"),
        20,
        1,
        10,
        20
    );
    
    when(producerService.getProducerReviews(eq(producerId), any(Pageable.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/reviews/producers/" + producerId)
                .param("page", "1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", "customer-1").claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageNumber").value(1))
        .andExpect(jsonPath("$.pageSize").value(10))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.totalElements").value(20));
  }

  @Test
  void getProducerReviews_shouldRespectSizeParameter() throws Exception {
    UUID producerId = UUID.randomUUID();
    
    ProducerReviewsResponse response = ProducerReviewsResponse.builder()
        .averageRating(new BigDecimal("4.60"))
        .totalReviews(50)
        .reviews(buildReviewItems(20))
        .pageNumber(0)
        .pageSize(20)
        .totalPages(3)
        .totalElements(50L)
        .build();
    
    when(producerService.getProducerReviews(eq(producerId), any(Pageable.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/reviews/producers/" + producerId)
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", "customer-1").claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageSize").value(20))
        .andExpect(jsonPath("$.reviews.length()").value(20))
        .andExpect(jsonPath("$.totalPages").value(3));
  }

  @Test
  void getProducerReviews_shouldSupportDefaultPagination() throws Exception {
    UUID producerId = UUID.randomUUID();
    
    ProducerReviewsResponse response = buildProducerReviewsResponse(
        producerId,
        new BigDecimal("4.70"),
        15,
        0,
        10,
        15
    );
    
    when(producerService.getProducerReviews(eq(producerId), any(Pageable.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/reviews/producers/" + producerId)
                .contentType(MediaType.APPLICATION_JSON)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", "customer-1").claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageNumber").value(0))
        .andExpect(jsonPath("$.pageSize").value(10));
  }

  @Test
  void getProducerReviews_shouldReturnReviewsWithCorrectFields() throws Exception {
    UUID producerId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    OffsetDateTime createdAt = OffsetDateTime.now();
    
    ReviewItemResponse review = ReviewItemResponse.builder()
        .id(reviewId)
        .customerName("João Cliente")
        .rating((short) 4)
        .comment("Produto de qualidade")
        .createdAt(createdAt)
        .build();
    
    ProducerReviewsResponse response = ProducerReviewsResponse.builder()
        .averageRating(new BigDecimal("4.40"))
        .totalReviews(1)
        .reviews(List.of(review))
        .pageNumber(0)
        .pageSize(10)
        .totalPages(1)
        .totalElements(1L)
        .build();
    
    when(producerService.getProducerReviews(eq(producerId), any(Pageable.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/reviews/producers/" + producerId)
                .contentType(MediaType.APPLICATION_JSON)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", "customer-1").claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviews[0].id").value(reviewId.toString()))
        .andExpect(jsonPath("$.reviews[0].customerName").value("João Cliente"))
        .andExpect(jsonPath("$.reviews[0].rating").value(4))
        .andExpect(jsonPath("$.reviews[0].comment").value("Produto de qualidade"))
        .andExpect(jsonPath("$.reviews[0].createdAt").exists());
  }

  @Test
  void getProducerReviews_shouldReturnHighAverageRating() throws Exception {
    UUID producerId = UUID.randomUUID();
    
    ProducerReviewsResponse response = buildProducerReviewsResponse(
        producerId,
        new BigDecimal("4.95"),
        100,
        0,
        10,
        100
    );
    
    when(producerService.getProducerReviews(eq(producerId), any(Pageable.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/reviews/producers/" + producerId)
                .contentType(MediaType.APPLICATION_JSON)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", "customer-1").claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.averageRating").value(4.95))
        .andExpect(jsonPath("$.totalReviews").value(100));
  }

  @Test
  void getProducerReviews_shouldReturnLowAverageRating() throws Exception {
    UUID producerId = UUID.randomUUID();
    
    ProducerReviewsResponse response = buildProducerReviewsResponse(
        producerId,
        new BigDecimal("2.30"),
        5,
        0,
        10,
        5
    );
    
    when(producerService.getProducerReviews(eq(producerId), any(Pageable.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/reviews/producers/" + producerId)
                .contentType(MediaType.APPLICATION_JSON)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", "customer-1").claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.averageRating").value(2.30))
        .andExpect(jsonPath("$.totalReviews").value(5));
  }

  @Test
  void getProducerReviews_shouldHandleMultiplePages() throws Exception {
    UUID producerId = UUID.randomUUID();
    
    ProducerReviewsResponse response = ProducerReviewsResponse.builder()
        .averageRating(new BigDecimal("4.75"))
        .totalReviews(100)
        .reviews(buildReviewItems(10))
        .pageNumber(2)
        .pageSize(10)
        .totalPages(10)
        .totalElements(100L)
        .build();
    
    when(producerService.getProducerReviews(eq(producerId), any(Pageable.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            get("/reviews/producers/" + producerId)
                .param("page", "2")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", "customer-1").claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageNumber").value(2))
        .andExpect(jsonPath("$.totalPages").value(10))
        .andExpect(jsonPath("$.totalElements").value(100));
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  private ProducerReviewsResponse buildProducerReviewsResponse(
      UUID producerId,
      BigDecimal averageRating,
      int totalReviews,
      int pageNumber,
      int pageSize,
      long totalElements) {
    
    return ProducerReviewsResponse.builder()
        .averageRating(averageRating)
        .totalReviews(totalReviews)
        .reviews(buildReviewItems(Math.min(pageSize, (int) totalElements)))
        .pageNumber(pageNumber)
        .pageSize(pageSize)
        .totalPages((int) Math.ceil((double) totalElements / pageSize))
        .totalElements(totalElements)
        .build();
  }

  private List<ReviewItemResponse> buildReviewItems(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(i -> ReviewItemResponse.builder()
            .id(UUID.randomUUID())
            .customerName("Maria Cliente")
            .rating((short) 5)
            .comment("Ótimo produtor!")
            .createdAt(OffsetDateTime.now().minusDays(i))
            .build())
        .toList();
  }

  private User buildUser(String authSub, boolean active) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test Customer");
    user.setEmail("customer@test.com");
    user.setPhone("51999999999");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(active);
    user.setAuthSub(authSub);
    user.setCreatedAt(OffsetDateTime.now().minusDays(1));
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }
}
