package br.com.ragro.controller;

import br.com.ragro.controller.request.UserRequest;
import br.com.ragro.controller.response.UserResponse;
import br.com.ragro.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

  private final UserService userService;

  public AdminUserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping
  public ResponseEntity<UserResponse> addUser(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UserRequest request) {
    UserResponse response = userService.addUser(jwt, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
