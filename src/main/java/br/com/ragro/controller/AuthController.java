package br.com.ragro.controller;

import br.com.ragro.controller.request.CustomerRegistrationRequest;
import br.com.ragro.controller.response.CustomerRegistrationResponse;
import br.com.ragro.service.CustomerRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Authentication", description = "User registration and authentication")
public class AuthController {

  private final CustomerRegistrationService customerRegistrationService;

  public AuthController(CustomerRegistrationService customerRegistrationService) {
    this.customerRegistrationService = customerRegistrationService;
  }

  @PostMapping("/register/customer")
  @Operation(
      summary = "Register a new customer",
      description = "Creates a customer account in Keycloak and the database. No auth required.")
  public ResponseEntity<CustomerRegistrationResponse> registerCustomer(
      @Valid @RequestBody CustomerRegistrationRequest request) {
    CustomerRegistrationResponse response = customerRegistrationService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
