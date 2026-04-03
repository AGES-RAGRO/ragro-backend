package br.com.ragro.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("isAuthenticated()")
public class RoleAccessController {

  @GetMapping("/admin/dashboard")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> adminDashboard(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(buildClaimsPayload("ADMIN", jwt));
  }

  @GetMapping("/farmer/dashboard")
  @PreAuthorize("hasRole('FARMER')")
  public ResponseEntity<Map<String, Object>> farmerDashboard(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(buildClaimsPayload("FARMER", jwt));
  }

  @GetMapping("/customers/orders")
  @PreAuthorize("hasRole('CUSTOMER')")
  public ResponseEntity<Map<String, Object>> customerOrders(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(buildClaimsPayload("CUSTOMER", jwt));
  }

  private Map<String, Object> buildClaimsPayload(String area, Jwt jwt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("area", area);
    payload.put("sub", jwt.getClaimAsString("sub"));
    payload.put("email", jwt.getClaimAsString("email"));
    payload.put("groups", getGroups(jwt));
    return payload;
  }

  private List<String> getGroups(Jwt jwt) {
    List<String> groups = jwt.getClaimAsStringList("groups");
    return groups == null ? List.of() : groups;
  }
}
