package br.com.ragro.controller;

import br.com.ragro.controller.response.UserResponse;
import br.com.ragro.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Authenticated user operations")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  @Operation(
      summary = "Get authenticated user profile",
      description = "Returns the profile of the currently logged-in user. Any valid JWT.")
  public ResponseEntity<UserResponse> getMyUser(@AuthenticationPrincipal Jwt jwt) {
    UserResponse response = userService.getMyUser(jwt);
    return ResponseEntity.ok(response);
  }
}
