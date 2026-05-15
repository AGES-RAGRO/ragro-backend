package br.com.ragro.controller;

import br.com.ragro.controller.response.FavoriteProducerResponse;
import br.com.ragro.service.FavoriteProducerService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers/me/favorites")
public class FavoriteProducerController {

    private final FavoriteProducerService favoriteProducerService;

    public FavoriteProducerController(
        FavoriteProducerService favoriteProducerService
    ) {
        this.favoriteProducerService = favoriteProducerService;
    }

    @PostMapping("/{producerId}")
    public ResponseEntity<Void> favoriteProducer(
        @PathVariable UUID producerId,
        @AuthenticationPrincipal Jwt jwt
    ) {

        favoriteProducerService.favoriteProducer(
            producerId,
            jwt
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{producerId}")
    public ResponseEntity<Void> unfavoriteProducer(
        @PathVariable UUID producerId,
        @AuthenticationPrincipal Jwt jwt
    ) {

        favoriteProducerService.unfavoriteProducer(
            producerId,
            jwt
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<FavoriteProducerResponse>> getFavorites(
        @AuthenticationPrincipal Jwt jwt
    ) {

        return ResponseEntity.ok(
            favoriteProducerService.getFavorites(jwt)
        );
    }
}
