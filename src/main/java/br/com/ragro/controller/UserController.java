package br.com.ragro.controller;

import br.com.ragro.controller.response.UserResponse;
import br.com.ragro.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyUser(@AuthenticationPrincipal Jwt jwt) {
        UserResponse response = userService.getMyUser(jwt);
        return ResponseEntity.ok(response);
    }
}