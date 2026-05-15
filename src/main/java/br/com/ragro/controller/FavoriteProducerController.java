package br.com.ragro.controller;

import br.com.ragro.controller.response.FavoriteProducerResponse;
import br.com.ragro.service.FavoriteProducerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers/me/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorite Producers", description = "Favorite producer management for customers")
public class FavoriteProducerController {

  private final FavoriteProducerService favoriteProducerService;

  @PostMapping("/{producerId}")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(summary = "Favorite a producer")
  public ResponseEntity<Void> favoriteProducer(
      @PathVariable UUID producerId, @AuthenticationPrincipal Jwt jwt) {

    favoriteProducerService.favoriteProducer(producerId, jwt);

    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{producerId}")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(summary = "Unfavorite a producer")
  public ResponseEntity<Void> unfavoriteProducer(
      @PathVariable UUID producerId, @AuthenticationPrincipal Jwt jwt) {

    favoriteProducerService.unfavoriteProducer(producerId, jwt);

    return ResponseEntity.noContent().build();
  }

  @GetMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(summary = "List authenticated customer's favorite producers")
  public ResponseEntity<List<FavoriteProducerResponse>> getFavorites(
      @AuthenticationPrincipal Jwt jwt) {

    return ResponseEntity.ok(favoriteProducerService.getFavorites(jwt));
  }
}
