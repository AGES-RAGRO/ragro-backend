package br.com.ragro.controller;

import br.com.ragro.controller.request.CustomerRegistrationRequest;
import br.com.ragro.controller.request.CustomerUpdateRequest;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.controller.response.CustomerRegistrationResponse;
import br.com.ragro.service.CustomerRegistrationService;
import br.com.ragro.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
@Tag(name = "Customers", description = "Customer operations (requires ROLE_CUSTOMER)")
public class CustomerController {

  private final CustomerRegistrationService customerRegistrationService;
  private final CustomerService customerService;

  public CustomerController(
      CustomerRegistrationService customerRegistrationService, CustomerService customerService) {
    this.customerRegistrationService = customerRegistrationService;
    this.customerService = customerService;
  }

  @PostMapping()
  @Operation(
      summary = "Register a new customer",
      description = "Creates a customer account in Keycloak and the database. No auth required.")
  public ResponseEntity<CustomerRegistrationResponse> registerCustomer(
      @Valid @RequestBody CustomerRegistrationRequest request) {
    CustomerRegistrationResponse response = customerRegistrationService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/me")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Get customer profile",
      description = "Returns the customer profile with personal data and addresses.")
  public ResponseEntity<CustomerResponse> getMyCustomer(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(customerService.getMyCustomer(jwt));
  }

  @PutMapping("/me")
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Update customer profile",
      description =
          "Updates the authenticated customer's name, phone, and primary address. Requires ROLE_CUSTOMER.")
  public ResponseEntity<CustomerResponse> updateMyCustomer(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CustomerUpdateRequest request) {
    return ResponseEntity.ok(customerService.updateMyCustomer(jwt, request));
  }
}
