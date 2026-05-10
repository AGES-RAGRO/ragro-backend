package br.com.ragro.controller;

import br.com.ragro.controller.request.CreateReviewRequest;
import br.com.ragro.controller.response.ErrorResponse;
import br.com.ragro.controller.response.ReviewResponse;
import br.com.ragro.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
@Tag(name = "Review", description = "Review operations")
public class ReviewController {

  private final ReviewService reviewService;

  @PostMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Create review for delivered order",
      description =
          "Creates a review for a delivered order that belongs to the authenticated customer.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Review created successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request payload or order is not delivered",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Inactive account or user not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Access denied",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Order not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Review already exists for this order",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<ReviewResponse> createReview(
      @Valid @RequestBody CreateReviewRequest request, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(request, jwt));
  }
}
