package br.com.ragro.controller;

import br.com.ragro.controller.request.ProducerFilter;
import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.controller.response.MarketplaceProducerResponse;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProductResponse;
import br.com.ragro.service.ProducerService;
import br.com.ragro.service.ProductService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/producers")
@RequiredArgsConstructor
@Tag(name = "Producer", description = "Producer operations")
public class ProducerController {

  private final ProducerService producerService;
  private final ProductService productService;

  @GetMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(summary = "List active producers for marketplace", description = "Returns a paginated list of active producers, sorted by rating desc. Restricted to Customers.")
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
  @Operation(summary = "Get producer by ID", description = "Returns consolidated producer profile. Farmer can only read their own profile; admin can read any.")
  public ResponseEntity<ProducerGetResponse> getProducerById(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(producerService.getProducerProfileById(id, jwt));
  }

  @GetMapping("/{id}/products")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "List active products of a producer",
      description = "Returns all active products of a producer. Restricted to Customers.")
  public ResponseEntity<List<ProductResponse>> getProducerProducts(@PathVariable UUID id) {
    return ResponseEntity.ok(productService.getActiveProductsByProducerId(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
  @Operation(summary = "Update producer profile", description = "Updates the authenticated producer's own profile. Only the owner can update their data.")
  public ResponseEntity<ProducerGetResponse> updateProducerProfile(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ProducerUpdateRequest request) {
    return ResponseEntity.ok(producerService.updateProducerProfile(id, jwt, request));
  }

  @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
  @Operation(summary = "Upload producer avatar", description = "Uploads a new profile photo (avatar) for the producer and replaces the previous one.")
  public ResponseEntity<ProducerGetResponse> uploadAvatar(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(producerService.updateAvatarPhoto(id, jwt, file));
  }

  @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
  @Operation(summary = "Upload producer cover photo", description = "Uploads a new cover/background photo for the producer and replaces the previous one.")
  public ResponseEntity<ProducerGetResponse> uploadCover(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(producerService.updateCoverPhoto(id, jwt, file));
  }
}
