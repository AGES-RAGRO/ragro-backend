package br.com.ragro.mapper;

import br.com.ragro.controller.response.ReviewResponse;
import br.com.ragro.domain.Review;

public class ReviewMapper {

  private ReviewMapper() {}

  public static ReviewResponse toResponse(Review review) {
    if (review == null) return null;

    return new ReviewResponse(
        review.getId(),
        review.getCustomer().getUser().getName(),
        review.getRating() != null ? review.getRating().intValue() : null,
        review.getComment(),
        review.getOrder().getId(),
        review.getFarmer().getId(),
        review.getCustomer().getId(),
        review.getCreatedAt());
  }
}
