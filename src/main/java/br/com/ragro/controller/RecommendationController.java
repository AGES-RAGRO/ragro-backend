package br.com.ragro.controller;

import br.com.ragro.controller.request.RecommendationRequest;
import br.com.ragro.controller.response.ErrorResponse;
import br.com.ragro.controller.response.RecommendationResponse;
import br.com.ragro.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "Personalized product recommendations for customers")
public class RecommendationController {

  private final RecommendationService recommendationService;

  @GetMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Get product recommendations",
      description =
          "Returns a ranked list of personalized product recommendations for the authenticated customer.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Recommendations returned successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request parameters",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Inactive account or user not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Access denied – only customers can request recommendations",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<RecommendationResponse> getRecommendations(
      @ModelAttribute @Valid RecommendationRequest request, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(recommendationService.getRecommendations(request, jwt));
  }
}
