package br.com.ragro.service;

import br.com.ragro.controller.request.CreateReviewRequest;
import br.com.ragro.controller.response.PaginatedResponse;
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
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.ReviewRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {
  private final OrderRepository orderRepository;
  private final ReviewRepository reviewRepository;
  private final ProducerRepository producerRepository;
  private final UserService userService;
  private final ProducerService producerService;

  @Transactional(readOnly = true)
  public PaginatedResponse<ReviewResponse> getReviewsByProducer(
      UUID producerId, Pageable pageable) {
    producerRepository
        .findById(producerId)
        .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    Page<ReviewResponse> page =
        reviewRepository.findAllByFarmerId(producerId, pageable).map(ReviewMapper::toResponse);

    return PaginatedResponse.of(page);
  }

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
    review.setOrder(order);
    review.setFarmer(order.getFarmer());
    review.setCustomer(order.getCustomer());
    review.setRating(request.rating().shortValue());
    review.setComment(normalizeComment(request.comment()));

    Review savedReview = reviewRepository.saveAndFlush(review);
    producerService.updateReviewStats(order.getFarmer().getId());

    return ReviewMapper.toResponse(savedReview);
  }

  private String normalizeComment(String comment) {
    if (comment == null) {
      return null;
    }

    String normalized = comment.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
