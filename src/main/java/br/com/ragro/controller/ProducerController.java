package br.com.ragro.controller;

import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.service.ProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/producers")
@RequiredArgsConstructor
public class ProducerController {

    private final ProducerService producerService;

    @GetMapping("/{id}")
    public ResponseEntity<ProducerGetResponse> getProducerById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var producer = producerService.findById(id)
            .orElseThrow(() -> new RuntimeException("Produtor não encontrado"));

        return ResponseEntity.ok(ProducerGetResponse.builder()
                .id(producer.getId())
                .name(producer.getName())
                .email(producer.getEmail())
                .phone(producer.getPhone())
                .fiscalNumber(producer.getFiscalNumber())
                .build());
    }
}