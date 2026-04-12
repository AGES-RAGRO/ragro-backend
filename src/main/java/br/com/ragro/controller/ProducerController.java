package br.com.ragro.controller;

import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.service.ProducerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/producers")
@RequiredArgsConstructor
@Tag(name = "Producer", description = "Producer operations (requires ROLE_FARMER)")
public class ProducerController {

  private final ProducerService producerService;

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('FARMER')")
  @Operation(
      summary = "Get producer by ID",
      description = "Returns consolidated producer profile. Farmer can only read their own profile; admin can read any.")
  public ResponseEntity<ProducerGetResponse> getProducerById(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(producerService.getProducerProfileById(id, jwt));
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
}
