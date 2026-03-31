package br.com.ragro.controller;

import br.com.ragro.controller.request.ConsumerRegistrationRequest;
import br.com.ragro.controller.response.ConsumerRegistrationResponse;
import br.com.ragro.service.ConsumerRegistrationService;
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

    private final ConsumerRegistrationService consumerRegistrationService;

    public AuthController(ConsumerRegistrationService consumerRegistrationService) {
        this.consumerRegistrationService = consumerRegistrationService;
    }

    @PostMapping("/register/consumer")
    public ResponseEntity<ConsumerRegistrationResponse> registerConsumer(
            @Valid @RequestBody ConsumerRegistrationRequest request
    ) {
        ConsumerRegistrationResponse response = consumerRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
