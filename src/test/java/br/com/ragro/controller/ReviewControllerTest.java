package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.request.CreateReviewRequest;
import br.com.ragro.controller.response.ReviewResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.ConflictException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
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

@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class ReviewControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private ReviewService reviewService;
  @MockBean private UserRepository userRepository;

  @Test
  void createReview_shouldReturn201_whenPayloadIsValid() throws Exception {
    UUID orderId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    UUID producerId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    String sub = "customer-sub";
    CreateReviewRequest request =
        new CreateReviewRequest(orderId, 5, "Great quality and quick delivery.");
    ReviewResponse response =
        new ReviewResponse(
            reviewId,
            5,
            "Great quality and quick delivery.",
            orderId,
            producerId,
            customerId,
            OffsetDateTime.parse("2026-05-06T18:30:00Z"));

    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomerUser(sub, true)));
    when(reviewService.createReview(eq(request), any())).thenReturn(response);

    mockMvc
        .perform(
            post("/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@ragro.com.br"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(reviewId.toString()))
        .andExpect(jsonPath("$.orderId").value(orderId.toString()))
        .andExpect(jsonPath("$.farmerId").value(producerId.toString()))
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.rating").value(5))
        .andExpect(jsonPath("$.comment").value("Great quality and quick delivery."));
  }

  @Test
  void createReview_shouldReturn400_whenPayloadIsInvalid() throws Exception {
    String sub = "customer-sub";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomerUser(sub, true)));

    mockMvc
        .perform(
            post("/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":6}")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@ragro.com.br"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void createReview_shouldReturn404_whenOrderIsNotFound() throws Exception {
    UUID orderId = UUID.randomUUID();
    String sub = "customer-sub";
    CreateReviewRequest request = new CreateReviewRequest(orderId, 4, "Ok");

    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomerUser(sub, true)));
    when(reviewService.createReview(eq(request), any()))
        .thenThrow(new NotFoundException("Order not found"));

    mockMvc
        .perform(
            post("/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@ragro.com.br"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Order not found"));
  }

  @Test
  void createReview_shouldReturn409_whenReviewAlreadyExists() throws Exception {
    UUID orderId = UUID.randomUUID();
    String sub = "customer-sub";
    CreateReviewRequest request = new CreateReviewRequest(orderId, 4, "Ok");

    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(buildCustomerUser(sub, true)));
    when(reviewService.createReview(eq(request), any()))
        .thenThrow(new ConflictException("Review already exists for this order"));

    mockMvc
        .perform(
            post("/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@ragro.com.br"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Review already exists for this order"));
  }

  @Test
  void createReview_shouldReturn401_whenNoTokenIsProvided() throws Exception {
    mockMvc
        .perform(
            post("/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"550e8400-e29b-41d4-a716-446655440000\",\"rating\":5}"))
        .andExpect(status().isUnauthorized());
  }

  private User buildCustomerUser(String authSub, boolean active) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Customer Test");
    user.setEmail("customer@ragro.com.br");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(active);
    user.setAuthSub(authSub);
    return user;
  }
}
