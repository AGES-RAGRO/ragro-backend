package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.CreateReviewRequest;
import br.com.ragro.controller.response.ReviewResponse;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Review;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ConflictException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.ReviewRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private ReviewRepository reviewRepository;
  @Mock private UserService userService;
  @Mock private ProducerService producerService;

  @InjectMocks private ReviewService reviewService;

  private Jwt jwt;
  private User authenticatedCustomer;
  private UUID customerId;
  private UUID producerId;
  private UUID orderId;

  @BeforeEach
  void setUp() {
    customerId = UUID.randomUUID();
    producerId = UUID.randomUUID();
    orderId = UUID.randomUUID();
    jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", "customer-sub")
            .claim("email", "customer@ragro.com.br")
            .build();

    authenticatedCustomer = new User();
    authenticatedCustomer.setId(customerId);
    authenticatedCustomer.setType(TypeUser.CUSTOMER);
    authenticatedCustomer.setActive(true);
  }

  @Test
  void createReview_shouldPersistReviewAndUpdateProducerStats_whenOrderIsDelivered() {
    CreateReviewRequest request =
        new CreateReviewRequest(orderId, 5, "  Great quality and quick delivery.  ");
    Order order = buildDeliveredOrder(orderId, customerId, producerId);

    Review savedReview = new Review();
    savedReview.setId(UUID.randomUUID());
    savedReview.setOrder(order);
    savedReview.setFarmer(order.getFarmer());
    savedReview.setCustomer(order.getCustomer());
    savedReview.setRating((short) 5);
    savedReview.setComment("Great quality and quick delivery.");
    savedReview.setCreatedAt(OffsetDateTime.parse("2026-05-06T18:30:00Z"));

    when(userService.getAuthenticatedUser(jwt)).thenReturn(authenticatedCustomer);
    when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
    when(reviewRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
    when(reviewRepository.saveAndFlush(any(Review.class))).thenReturn(savedReview);

    ReviewResponse result = reviewService.createReview(request, jwt);

    assertThat(result.id()).isEqualTo(savedReview.getId());
    assertThat(result.authorName()).isEqualTo("Customer Test");
    assertThat(result.rating()).isEqualTo(5);
    assertThat(result.comment()).isEqualTo("Great quality and quick delivery.");
    assertThat(result.orderId()).isEqualTo(orderId);
    assertThat(result.farmerId()).isEqualTo(producerId);
    assertThat(result.customerId()).isEqualTo(customerId);

    verify(reviewRepository).saveAndFlush(any(Review.class));
    verify(producerService).updateReviewStats(producerId);
  }

  @Test
  void createReview_shouldThrowNotFound_whenOrderDoesNotBelongToAuthenticatedCustomer() {
    CreateReviewRequest request = new CreateReviewRequest(orderId, 5, "Great");

    when(userService.getAuthenticatedUser(jwt)).thenReturn(authenticatedCustomer);
    when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> reviewService.createReview(request, jwt))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Order not found");

    verify(reviewRepository, never()).save(any(Review.class));
    verify(producerService, never()).updateReviewStats(any(UUID.class));
  }

  @Test
  void createReview_shouldThrowBusinessException_whenOrderIsNotDelivered() {
    CreateReviewRequest request = new CreateReviewRequest(orderId, 4, "Ok");
    Order order = buildDeliveredOrder(orderId, customerId, producerId);
    order.setStatus(OrderStatus.CONFIRMED);

    when(userService.getAuthenticatedUser(jwt)).thenReturn(authenticatedCustomer);
    when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> reviewService.createReview(request, jwt))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Review is only allowed for delivered orders");

    verify(reviewRepository, never()).save(any(Review.class));
    verify(producerService, never()).updateReviewStats(any(UUID.class));
  }

  @Test
  void createReview_shouldThrowConflictException_whenReviewAlreadyExistsForOrder() {
    CreateReviewRequest request = new CreateReviewRequest(orderId, 4, "Ok");
    Order order = buildDeliveredOrder(orderId, customerId, producerId);

    when(userService.getAuthenticatedUser(jwt)).thenReturn(authenticatedCustomer);
    when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
    when(reviewRepository.findByOrderId(orderId)).thenReturn(Optional.of(new Review()));

    assertThatThrownBy(() -> reviewService.createReview(request, jwt))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Review already exists for this order");

    verify(reviewRepository, never()).save(any(Review.class));
    verify(producerService, never()).updateReviewStats(any(UUID.class));
  }

  @Test
  void createReview_shouldNormalizeBlankCommentToNull() {
    CreateReviewRequest request = new CreateReviewRequest(orderId, 3, "   ");
    Order order = buildDeliveredOrder(orderId, customerId, producerId);
    Review savedReview = new Review();
    savedReview.setId(UUID.randomUUID());
    savedReview.setOrder(order);
    savedReview.setFarmer(order.getFarmer());
    savedReview.setCustomer(order.getCustomer());
    savedReview.setRating((short) 3);

    when(userService.getAuthenticatedUser(jwt)).thenReturn(authenticatedCustomer);
    when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
    when(reviewRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
    when(reviewRepository.saveAndFlush(any(Review.class)))
        .thenAnswer(
            invocation -> {
              Review review = invocation.getArgument(0);
              assertThat(review.getComment()).isNull();
              return savedReview;
            });

    ReviewResponse result = reviewService.createReview(request, jwt);

    assertThat(result.authorName()).isEqualTo("Customer Test");
    assertThat(result.comment()).isNull();
    verify(producerService).updateReviewStats(producerId);
  }

  private Order buildDeliveredOrder(UUID orderId, UUID customerId, UUID producerId) {
    User customerUser = new User();
    customerUser.setId(customerId);
    customerUser.setName("Customer Test");

    Customer customer = new Customer();
    customer.setId(customerId);
    customer.setUser(customerUser);

    User producerUser = new User();
    producerUser.setId(producerId);
    Producer producer = new Producer();
    producer.setId(producerId);
    producer.setUser(producerUser);

    Order order = new Order();
    order.setId(orderId);
    order.setCustomer(customer);
    order.setFarmer(producer);
    order.setStatus(OrderStatus.DELIVERED);
    return order;
  }
}
