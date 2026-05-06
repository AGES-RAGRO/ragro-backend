package br.com.ragro.service;

import br.com.ragro.controller.request.CreateReviewRequest;
import br.com.ragro.controller.response.ReviewResponse;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.Review;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ConflictException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.ReviewMapper;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

  private final OrderRepository orderRepository;
  private final ReviewRepository reviewRepository;
  private final UserService userService;
  private final ProducerService producerService;
  private final ReviewMapper reviewMapper;

  @Transactional
  public ReviewResponse createReview(CreateReviewRequest request, Jwt jwt) {
    User authenticatedUser = userService.getAuthenticatedUser(jwt);

    Order order =
        orderRepository
            .findByIdAndCustomerId(request.orderId(), authenticatedUser.getId())
            .orElseThrow(() -> new NotFoundException("Order not found"));

    if (order.getStatus() != OrderStatus.DELIVERED) {
      throw new BusinessException("Review is only allowed for delivered orders");
    }

    if (reviewRepository.findByOrderId(request.orderId()).isPresent()) {
      throw new ConflictException("Review already exists for this order");
    }

    Review review = new Review();
    review.setOrderId(order.getId());
    review.setFarmerId(order.getFarmer().getId());
    review.setCustomerId(authenticatedUser.getId());
    review.setRating(request.rating().shortValue());
    review.setComment(normalizeComment(request.comment()));

    Review savedReview = reviewRepository.saveAndFlush(review);
    producerService.updateReviewStats(order.getFarmer().getId());

    return reviewMapper.toResponse(savedReview);
  }

  private String normalizeComment(String comment) {
    if (comment == null) {
      return null;
    }

    String normalized = comment.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
