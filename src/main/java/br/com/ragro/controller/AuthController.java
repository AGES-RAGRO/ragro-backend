package br.com.ragro.controller;

import br.com.ragro.controller.request.CustomerRegistrationRequest;
import br.com.ragro.controller.request.ForgotPasswordRequest;
import br.com.ragro.controller.response.AuthConfigResponse;
import br.com.ragro.controller.response.CustomerRegistrationResponse;
import br.com.ragro.controller.response.SessionResponse;
import br.com.ragro.domain.User;
import br.com.ragro.service.CustomerRegistrationService;
import br.com.ragro.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "User registration and authentication")
public class AuthController {

  private final CustomerRegistrationService customerRegistrationService;
  private final UserService userService;
  private final String keycloakPublicUrl;
  private final String realm;

  public AuthController(
      CustomerRegistrationService customerRegistrationService,
      UserService userService,
      @Value("${keycloak.public-url}") String keycloakPublicUrl,
      @Value("${keycloak.realm}") String realm) {
    this.customerRegistrationService = customerRegistrationService;
    this.userService = userService;
    this.keycloakPublicUrl = keycloakPublicUrl;
    this.realm = realm;
  }

  @PostMapping("/register/customer")
  @Operation(summary = "Register a new customer", description = "Creates a customer account in Keycloak and the database. No auth required.")
  public ResponseEntity<CustomerRegistrationResponse> registerCustomer(
      @Valid @RequestBody CustomerRegistrationRequest request) {
    CustomerRegistrationResponse response = customerRegistrationService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/config")
  @Operation(summary = "Get authentication configuration", description = "Returns Keycloak token URL, client ID, and realm. No auth required.")
  public ResponseEntity<AuthConfigResponse> getConfig() {
    String tokenUrl = keycloakPublicUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    return ResponseEntity.ok(
        AuthConfigResponse.builder().tokenUrl(tokenUrl).clientId("ragro-app").realm(realm).build());
  }

  @GetMapping("/session")
  @Operation(summary = "Get current user session", description = "Returns the authenticated user's data from the database. Requires valid JWT.")
  public ResponseEntity<SessionResponse> getSession(@AuthenticationPrincipal Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    return ResponseEntity.ok(
        SessionResponse.builder()
            .id(user.getId())
            .name(user.getName())
            .email(user.getEmail())
            .type(user.getType().name().toLowerCase(Locale.ROOT))
            .active(user.isActive())
            .build());
  }

  @PostMapping("/password/reset")
  @Operation(summary = "Trigger password reset for current user", description = "Sends a password reset email to the authenticated user. Requires valid JWT.")
  public ResponseEntity<Void> triggerPasswordReset(@AuthenticationPrincipal Jwt jwt) {
    userService.triggerPasswordReset(jwt);
    return ResponseEntity.noContent().build();
  }
  
  @PostMapping("/password/forgot")
  @Operation(summary = "Forgot password", description = "Triggers a password reset email for the user with the given email. Public endpoint.")
  public ResponseEntity<Void> triggerForgotPasswordEmail(
      @Valid @RequestBody ForgotPasswordRequest request) {
    userService.forgotPassword(request.email());
    return ResponseEntity.noContent().build();
  }
}
