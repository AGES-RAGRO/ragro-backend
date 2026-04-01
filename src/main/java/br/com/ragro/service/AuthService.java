package br.com.ragro.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.entity.User;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.controller.response.LoginResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    
    public LoginResponse authenticate(String email, String password) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));
        
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new UnauthorizedException("Senha inválida");
        }
        
        String token = jwtTokenProvider.generateToken(user);
        
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(mapToUserDto(user));
        return response;
    }
    
    private LoginResponse.UserDto mapToUserDto(User user) {
        LoginResponse.UserDto userDto = new LoginResponse.UserDto();
        userDto.setId(user.getId());
        userDto.setName(user.getName());
        userDto.setEmail(user.getEmail());
        userDto.setPhone(user.getPhone());
        userDto.setType(user.getType().name().toLowerCase());
        userDto.setActive(user.isActive());
        return userDto;
    }
}
