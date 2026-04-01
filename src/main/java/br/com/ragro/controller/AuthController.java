package br.com.ragro.controller;

import br.com.ragro.controller.request.ConsumerRegistrationRequest;
import br.com.ragro.controller.response.ConsumerRegistrationResponse;
import br.com.ragro.service.ConsumerRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "User authentication and registration endpoints")
public class AuthController {

    private final ConsumerRegistrationService consumerRegistrationService;

    public AuthController(ConsumerRegistrationService consumerRegistrationService) {
        this.consumerRegistrationService = consumerRegistrationService;
    }

    @PostMapping("/register/consumer")
    @Operation(summary = "Register a new consumer",
            description = "Creates a new consumer (customer) account with email, phone, fiscal number (CPF), and primary address. " +
                    "The email and fiscal number must be unique in the system.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Consumer registered successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConsumerRegistrationResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Validation failed or business rule violated (e.g., duplicate email/CPF, invalid password, missing address)",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500",
                    description = "Internal server error")
    })
    public ResponseEntity<ConsumerRegistrationResponse> registerConsumer(
            @Valid @RequestBody ConsumerRegistrationRequest request
    ) {
        ConsumerRegistrationResponse response = consumerRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
