package br.com.ragro.controller;

import br.com.ragro.controller.request.ProducerFilter;
import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.controller.response.DashboardWeeklyResponse;
import br.com.ragro.controller.response.LocationResponse;
import br.com.ragro.controller.response.MarketplaceProducerResponse;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.ProducerDashboardResponse;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerPublicProfileResponse;
import br.com.ragro.controller.response.ProductResponse;
import br.com.ragro.controller.response.ReviewResponse;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.service.DashboardService;
import br.com.ragro.service.ProducerService;
import br.com.ragro.service.ProductService;
import br.com.ragro.service.ReviewService;
import br.com.ragro.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/producers")
@RequiredArgsConstructor
@Tag(name = "Producer", description = "Producer operations")
public class ProducerController {

  private final ProducerService producerService;
  private final ProductService productService;
  private final ReviewService reviewService;
  private final DashboardService dashboardService;
  private final UserService userService;

  @GetMapping("/locations")
  @Operation(
      summary = "List producer locations",
      description =
          "Returns a list of all active producer coordinates to be plotted on the map. Accessible to all authenticated users.")
  public ResponseEntity<List<LocationResponse>> getProducerLocations() {
    return ResponseEntity.ok(producerService.getProducerLocations());
  }

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

  @GetMapping("/{id}/reviews")
  @PreAuthorize("hasAnyRole('CUSTOMER', 'FARMER')")
  @Operation(
      summary = "List reviews of a producer",
      description =
          "Returns a paginated list of reviews for a producer. Restricted to Customers and Farmers.")
  public ResponseEntity<PaginatedResponse<ReviewResponse>> getProducerReviews(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(reviewService.getReviewsByProducer(id, PageRequest.of(page, size)));
  }

  @GetMapping("/me/dashboard")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Get authenticated producer's financial dashboard",
      description =
          "Returns consolidated financial dashboard data for the authenticated producer. "
              + "Includes total sales, delivered orders, and stock sold percentage for the selected month, "
              + "all with comparisons to the previous month. Defaults to current month if not specified.")
  public ResponseEntity<ProducerDashboardResponse> getProducerDashboard(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Integer month,
      @RequestParam(required = false) Integer year) {
    if ((month == null) ^ (year == null)) {
      throw new BusinessException("Both 'month' and 'year' must be provided together");
    }

    if (month != null) {
      if (month < 1 || month > 12) {
        throw new BusinessException("'month' must be between 1 and 12");
      }
      if (year < 1900 || year > 2100) {
        throw new BusinessException("'year' is out of allowed range");
      }
    }

    UUID producerId = userService.getAuthenticatedUser(jwt).getId();
    return ResponseEntity.ok(dashboardService.getProducerDashboard(producerId, month, year));
  }

  @GetMapping("/me/dashboard/week")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Get authenticated producer's weekly sales dashboard",
      description =
          "Returns daily sales data for the last 7 days (including today). "
              + "Each day includes order count and total sales amount.")
  public ResponseEntity<DashboardWeeklyResponse> getWeeklyDashboard(
      @AuthenticationPrincipal Jwt jwt) {
    UUID producerId = userService.getAuthenticatedUser(jwt).getId();
    return ResponseEntity.ok(dashboardService.getWeeklyDashboard(producerId));
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
