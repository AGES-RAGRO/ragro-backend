package br.com.ragro.controller;

import br.com.ragro.controller.response.ConsumerResponse;
import br.com.ragro.service.ConsumerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/consumer")
public class ConsumerController {

    private final ConsumerService consumerService;

    public ConsumerController(ConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @GetMapping("/me")
    public ResponseEntity<ConsumerResponse> getMyConsumer(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(consumerService.getMyConsumer(jwt));
    }
}
