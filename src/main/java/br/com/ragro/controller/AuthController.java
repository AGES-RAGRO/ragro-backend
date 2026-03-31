package br.com.ragro.controller;

import br.com.ragro.controller.request.ConsumerRegisterRequest;
import br.com.ragro.controller.response.ConsumerResponse;
import br.com.ragro.service.ConsumerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final ConsumerService consumerService;

    public AuthController(ConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @PostMapping("/register/consumer")
    public ResponseEntity<ConsumerResponse> registerConsumer(
            @Valid @RequestBody ConsumerRegisterRequest request) {
        ConsumerResponse response = consumerService.registerConsumer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
