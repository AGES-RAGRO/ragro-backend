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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/farmer")
@Tag(name = "Producer", description = "Producer operations (requires ROLE_FARMER)")
public class ProducerController {

  @GetMapping("/dashboard")
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
}
