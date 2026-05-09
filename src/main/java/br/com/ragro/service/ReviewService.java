package br.com.ragro.service;

import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.ReviewResponse;
import br.com.ragro.domain.Review;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.mapper.ReviewMapper;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.ReviewRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final ProducerRepository producerRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<ReviewResponse> getReviewsByProducer(UUID producerId, Pageable pageable) {
    producerRepository
        .findById(producerId)
        .orElseThrow(() -> new NotFoundException("Produtor não encontrado"));

    Page<ReviewResponse> page =
        reviewRepository
            .findAllByFarmerId(producerId, pageable)
            .map(ReviewMapper::toResponse);

    return PaginatedResponse.of(page);
  }
}