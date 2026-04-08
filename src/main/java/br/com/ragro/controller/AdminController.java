package br.com.ragro.controller;

import br.com.ragro.controller.request.UserRequest;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.controller.response.UserResponse;
import br.com.ragro.service.ProducerService;
import br.com.ragro.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Administrative operations (requires ROLE_ADMIN)")
public class AdminController {

  private final UserService userService;
  private final ProducerService producerService;

  public AdminController(UserService userService, ProducerService producerService) {
    this.userService = userService;
    this.producerService = producerService;
  }

  @PostMapping("/users")
  @Operation(summary = "Create a user", description = "Creates a new user with the given type.")
  public ResponseEntity<UserResponse> addUser(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UserRequest request) {
    UserResponse response = userService.addUser(jwt, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/producers/{id}")
  @Operation(
      summary = "Get producer details",
      description = "Returns the details of a specific producer by ID.")
  public ResponseEntity<ProducerResponse> getProducer(@PathVariable UUID id) {
    return ResponseEntity.ok(producerService.getProducerById(id));
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
}
