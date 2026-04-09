package br.com.ragro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import java.util.UUID;
import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.service.ProducerService;

@RestController
@Tag(name = "Producer", description = "Producer operations")
public class ProducerController {

  private final ProducerService producerService;

  public ProducerController(ProducerService producerService) {
    this.producerService = producerService;
  }

  @GetMapping("/farmer/dashboard")
  @Operation(
      summary = "Verify producer access",
      description = "Test endpoint — returns JWT claims. Will be replaced.")
  public ResponseEntity<Map<String, Object>> dashboard(@AuthenticationPrincipal Jwt jwt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("area", "FARMER");
    payload.put("sub", jwt.getClaimAsString("sub"));
    payload.put("email", jwt.getClaimAsString("email"));
    List<String> groups = jwt.getClaimAsStringList("groups");
    payload.put("groups", groups == null ? List.of() : groups);
    return ResponseEntity.ok(payload);
  }

  @PutMapping("/producers/{id}")
  @Operation(
      summary = "Update producer profile",
      description = "Updates the producer profile details. Only the producer themselves or an admin can perform this.")
  public ResponseEntity<ProducerResponse> updateProducer(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ProducerUpdateRequest request) {
    return ResponseEntity.ok(producerService.updateProducer(id, jwt, request));
  }
}
