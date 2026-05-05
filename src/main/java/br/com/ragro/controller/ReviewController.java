package br.com.ragro.controller;

import br.com.ragro.controller.response.ProducerReviewsResponse;
import br.com.ragro.service.ProducerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
@Tag(name = "Review", description = "Producer reviews operations")
public class ReviewController {

  private final ProducerService producerService;

  @GetMapping("/producers/{id}")
  @Operation(
      summary = "List producer reviews",
      description = "Returns paginated list of reviews for a producer with aggregated stats. Public endpoint."
  )
  public ResponseEntity<ProducerReviewsResponse> getProducerReviews(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "createdAt") String sortBy,
      @RequestParam(defaultValue = "DESC") String direction) {
      
      Sort.Direction sortDirection = Sort.Direction.fromString(direction.toUpperCase());
      var pageable = PageRequest.of(page, size, Sort.by(new Sort.Order(sortDirection, sortBy)));
      return ResponseEntity.ok(producerService.getProducerReviews(id, pageable));
  }
}
