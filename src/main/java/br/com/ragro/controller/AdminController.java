package br.com.ragro.controller;

import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.controller.response.PaginatedResponse;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerRegistrationResponse;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.service.CustomerService;
import br.com.ragro.service.ProducerRegistrationService;
import br.com.ragro.service.ProducerService;
import br.com.ragro.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Administrative operations (requires ROLE_ADMIN)")
public class AdminController {

  private final CustomerService customerService;
  private final ProducerService producerService;
  private final ProducerRegistrationService producerRegistrationService;

  public AdminController(
      UserService userService,
      CustomerService customerService,
      ProducerService producerService,
      ProducerRegistrationService producerRegistrationService) {
    this.customerService = customerService;
    this.producerService = producerService;
    this.producerRegistrationService = producerRegistrationService;
  }

  @GetMapping("/producers")
  @Operation(
      summary = "List all producers",
      description = "Returns a paginated list of all producers (active and inactive), sorted by rating desc.")
  public ResponseEntity<PaginatedResponse<ProducerResponse>> getProducers(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ResponseEntity.ok(
        PaginatedResponse.of(producerService.getAllProducers(PageRequest.of(page, size))));
  }

  @GetMapping("/producers/{id}")
  @Operation(
          summary = "Get producer details",
          description = "Returns the details of a specific producer by ID.")
  public ResponseEntity<ProducerGetResponse> getProducer(@PathVariable UUID id) {
    return ResponseEntity.ok(producerService.getProducerProfileById(id));
  }

  @GetMapping("/customers/{id}")
  @Operation(
      summary = "Get customer details",
      description = "Returns the details of a specific customer by ID.")
  public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID id) {
    return ResponseEntity.ok(customerService.getCustomerById(id));
  }

  @PutMapping("/producers/{id}")
  @Operation(
      summary = "Update producer profile (admin)",
      description = "Allows an admin to update any producer's profile including payment methods.")
  public ResponseEntity<ProducerGetResponse> updateProducer(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ProducerUpdateRequest request) {
    return ResponseEntity.ok(producerService.updateProducerProfile(id, jwt, request));
  }

  @PatchMapping("/producers/{id}/activate")
  @Operation(
      summary = "Activate a producer",
      description = "Sets the status of a producer to active by ID.")
  public ResponseEntity<ProducerResponse> activateProducer(@PathVariable UUID id) {
    return ResponseEntity.ok(producerService.activateProducer(id));
  }
  
  @PatchMapping("/producers/{id}/deactivate")
  @Operation(
      summary = "Deactivate a producer",
      description = "Sets the status of a producer to inactive by ID.")
  public ResponseEntity<ProducerResponse> deactivateProducer(@PathVariable UUID id) {
    return ResponseEntity.ok(producerService.deactivateProducer(id));
  }

  @GetMapping("/dashboard")
  @Operation(
          summary = "Verify admin access",
          description = "Test endpoint — returns JWT claims. Will be replaced.")
  public ResponseEntity<Map<String, Object>> dashboard(@AuthenticationPrincipal Jwt jwt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("area", "ADMIN");
    payload.put("sub", jwt.getClaimAsString("sub"));
    payload.put("email", jwt.getClaimAsString("email"));
    payload.put("groups", getGroups(jwt));
    return ResponseEntity.ok(payload);
  }

  private List<String> getGroups(Jwt jwt) {
    List<String> groups = jwt.getClaimAsStringList("groups");
    return groups == null ? List.of() : groups;
  }

  @PostMapping("/producers")
  @Operation(
          summary = "Register producer",
          description = "Creates a new producer profile in the system.")
  public ResponseEntity<ProducerRegistrationResponse> register(
          @Valid @RequestBody ProducerRegistrationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(producerRegistrationService.register(request));
  }
}
