package br.com.ragro.controller;

import br.com.ragro.controller.request.ProducerFilter;
import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.controller.response.ErrorResponse;
import br.com.ragro.controller.response.MarketplaceProducerResponse;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerPublicProfileResponse;
import br.com.ragro.controller.response.ProducerReviewsResponse;
import br.com.ragro.controller.response.ProductResponse;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.service.ProducerService;
import br.com.ragro.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/producers")
@RequiredArgsConstructor
@Tag(name = "Producer", description = "Producer operations")
public class ProducerController {

  private static final Set<String> ALLOWED_REVIEW_SORT_FIELDS = Set.of("createdAt", "rating");

  private final ProducerService producerService;
  private final ProductService productService;

  @GetMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "List active producers for marketplace",
      description =
          "Returns a paginated list of active producers, sorted by rating desc. Restricted to"
              + " Customers.")
  public ResponseEntity<PaginatedResponse<MarketplaceProducerResponse>> getActiveProducers(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @ModelAttribute ProducerFilter filter) {
    return ResponseEntity.ok(
        PaginatedResponse.of(
            producerService.getActiveProducers(filter, PageRequest.of(page, size))));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Get producer by ID",
      description =
          "Returns consolidated producer profile. Farmer can only read their own profile; admin can"
              + " read any.")
  public ResponseEntity<ProducerGetResponse> getProducerById(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(producerService.getProducerProfileById(id, jwt));
  }

  @GetMapping("/{id}/profile")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Get public producer profile",
      description =
          "Returns the public producer profile fields used by the customer-facing producer profile"
              + " screen.")
  public ResponseEntity<ProducerPublicProfileResponse> getPublicProducerProfile(
      @PathVariable UUID id) {
    return ResponseEntity.ok(producerService.getPublicProfileById(id));
  }

  @GetMapping("/{id}/products")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "List active products of a producer",
      description = "Returns all active products of a producer. Restricted to Customers.")
  public ResponseEntity<List<ProductResponse>> getProducerProducts(@PathVariable UUID id) {
    return ResponseEntity.ok(productService.getActiveProductsByProducerId(id));
  }

  @GetMapping("/{id}/reviews")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Get producer public reviews",
      description =
          "Returns the paginated public reviews used by the customer-facing producer profile screen.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Producer reviews returned successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid pagination or sorting parameters",
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
        description = "Producer not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<ProducerReviewsResponse> getProducerReviews(
      @PathVariable UUID id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) List<String> sort,
      @ParameterObject
          @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    validateReviewsRequest(page, size, sort, pageable);
    return ResponseEntity.ok(producerService.getProducerReviews(id, pageable));
  }

  private void validateReviewsRequest(
      Integer page, Integer size, List<String> sort, Pageable pageable) {
    if (page != null && page < 0) {
      throw new BusinessException("page must be greater than or equal to 0");
    }

    if (size != null && (size < 1 || size > 100)) {
      throw new BusinessException("size must be between 1 and 100");
    }

    if (sort != null) {
      for (String sortEntry : sort) {
        String property = sortEntry.split(",")[0];
        if (!ALLOWED_REVIEW_SORT_FIELDS.contains(property)) {
          throw new BusinessException("sort must use one of: createdAt, rating");
        }
      }
    }

    pageable
        .getSort()
        .forEach(
            order -> {
              if (!ALLOWED_REVIEW_SORT_FIELDS.contains(order.getProperty())) {
                throw new BusinessException("sort must use one of: createdAt, rating");
              }
            });
  }

  @GetMapping("/{producerId}/products/{productId}")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Get product details from a producer",
      description =
          "Returns details of a specific active product from a producer. Restricted to Customers.")
  public ResponseEntity<ProductResponse> getProducerProductById(
      @PathVariable UUID producerId, @PathVariable UUID productId) {
    return ResponseEntity.ok(
        productService.getActiveProductByProducerIdAndProductId(producerId, productId));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
  @Operation(
      summary = "Update producer profile",
      description =
          "Updates the authenticated producer's own profile. Only the owner can update their data.")
  public ResponseEntity<ProducerGetResponse> updateProducerProfile(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ProducerUpdateRequest request) {
    return ResponseEntity.ok(producerService.updateProducerProfile(id, jwt, request));
  }

  @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
  @Operation(
      summary = "Upload producer avatar",
      description =
          "Uploads a new profile photo (avatar) for the producer and replaces the previous one.")
  public ResponseEntity<ProducerGetResponse> uploadAvatar(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(producerService.updateAvatarPhoto(id, jwt, file));
  }

  @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
  @Operation(
      summary = "Upload producer cover photo",
      description =
          "Uploads a new cover/background photo for the producer and replaces the previous one.")
  public ResponseEntity<ProducerGetResponse> uploadCover(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(producerService.updateCoverPhoto(id, jwt, file));
  }
}
