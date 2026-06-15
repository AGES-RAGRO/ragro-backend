package br.com.ragro.controller;

import br.com.ragro.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Registro de tokens FCM para push notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/token")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Registra ou atualiza o token FCM do dispositivo do usuário autenticado")
    public ResponseEntity<Void> registerToken(
            @RequestBody @Valid TokenRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        notificationService.saveToken(jwt, body.token());
        return ResponseEntity.noContent().build();
    }

    public record TokenRequest(@NotBlank String token) {}

}

