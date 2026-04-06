package br.com.ragro.controller;

import br.com.ragro.controller.request.CustomerUpdateRequest;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
@Tag(name = "Customers", description = "Customer operations")
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping("/me")
  @Operation(
      summary = "Get customer profile",
      description = "Returns the authenticated customer's profile with personal data and addresses.")
  public ResponseEntity<CustomerResponse> getMyCustomer(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(customerService.getMyCustomer(jwt));
  }

  @PutMapping("/me")
  @Operation(
      summary = "Update customer profile",
      description =
          "Updates the authenticated customer's name, phone, and primary address. Requires ROLE_CUSTOMER.")
  public ResponseEntity<CustomerResponse> updateMyCustomer(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CustomerUpdateRequest request) {
    return ResponseEntity.ok(customerService.updateMyCustomer(jwt, request));
  }

  @GetMapping("/orders")
  @Operation(
      summary = "Verify customer access",
      description = "Test endpoint — returns JWT claims. Will be replaced.")
  public ResponseEntity<Map<String, Object>> orders(@AuthenticationPrincipal Jwt jwt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("area", "CUSTOMER");
    payload.put("sub", jwt.getClaimAsString("sub"));
    payload.put("email", jwt.getClaimAsString("email"));
    List<String> groups = jwt.getClaimAsStringList("groups");
    payload.put("groups", groups == null ? List.of() : groups);
    return ResponseEntity.ok(payload);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get customer by ID",
      description = "Returns a customer profile by ID.")
  public ResponseEntity<CustomerResponse> getCustomerById(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ResponseEntity.ok(customerService.getCustomerById(id, jwt));
  }
}
