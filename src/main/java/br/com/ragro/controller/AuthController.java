package br.com.ragro.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import br.com.ragro.controller.request.LoginRequest;
import br.com.ragro.controller.response.LoginResponse;
import br.com.ragro.service.AuthService;
@RestController 
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.authenticate(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(response);
    }
}