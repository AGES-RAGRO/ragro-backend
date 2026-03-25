package br.com.ragro.service;

import br.com.ragro.controller.request.UserRequest;
import br.com.ragro.controller.response.UserResponse;
import br.com.ragro.domain.User;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.mapper.UserMapper;
import br.com.ragro.repository.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static br.com.ragro.mapper.UserMapper.toResponse;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse addUser(Jwt jwt, UserRequest request) {
        String email = getRequiredClaim(jwt, "email");
        String sub = getRequiredClaim(jwt, "sub");

        if (userRepository.existsByEmail(email) || userRepository.existsByCognitoSub(sub)) {
            throw new BusinessException("E-mail já cadastrado");
        }

        User user = UserMapper.toEntity(request);
        user.setEmail(email);
        user.setCognitoSub(sub);
        user.setActive(true);

        User saved = userRepository.save(user);

        return UserMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse getMyUser(Jwt jwt) {
        User userAuthenticated = getAuthenticatedUser(jwt);
        return toResponse(userAuthenticated);
    }

    public User getAuthenticatedUser(Jwt jwt) {
        String sub = getRequiredClaim(jwt, "sub");
        return userRepository.findByCognitoSub(sub)
                .orElseGet(() -> userRepository.findByEmail(getRequiredClaim(jwt, "email"))
                        .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado")));
    }

    public String getRequiredClaim(Jwt jwt, String claimName) {
        String value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            throw new UnauthorizedException("Token inválido: claim obrigatória ausente: " + claimName);
        }
        return value;
    }
}
