package br.com.ragro.mapper;

import br.com.ragro.controller.response.ProducerReviewsResponse;
import br.com.ragro.controller.response.ReviewItemResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Review;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewMapper {

    public ReviewItemResponse toItemResponse(Review review, String customerName) {
        return ReviewItemResponse.builder()
            .id(review.getId())
            .customerName(customerName)
            .rating(review.getRating())
            .comment(review.getComment())
            .createdAt(review.getCreatedAt())
            .build();
    }

    public ProducerReviewsResponse toPageResponse(
            Page<Review> reviews,
            Producer producer,
            Map<UUID, String> customerNames) {
        
        List<ReviewItemResponse> items = reviews.getContent().stream()
            .map(r -> toItemResponse(r, customerNames.get(r.getCustomerId())))
            .toList();

        return ProducerReviewsResponse.builder()
            .averageRating(producer.getAverageRating())
            .totalReviews(producer.getTotalReviews())
            .reviews(items)
            .pageNumber(reviews.getNumber())
            .pageSize(reviews.getSize())
            .totalPages(reviews.getTotalPages())
            .totalElements(reviews.getTotalElements())
            .build();
    }
}
