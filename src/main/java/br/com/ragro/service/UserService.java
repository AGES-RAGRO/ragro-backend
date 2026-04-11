package br.com.ragro.service;

import br.com.ragro.controller.request.UpdateUserRequest;
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

@Service
public class UserService {

  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User getAuthenticatedUser(Jwt jwt) {
    String sub = getRequiredClaim(jwt, "sub");
    return userRepository
      .findByAuthSub(sub)
      .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));
  }

  @Transactional
  public User updateUser(User user, UpdateUserRequest request) {
    user.setName(request.getName().trim());
    if (request.getPhone() != null) {
      user.setPhone(request.getPhone().trim());
    }
    return userRepository.saveAndFlush(user);
  }

  public String getRequiredClaim(Jwt jwt, String claimName) {
    String value = jwt.getClaimAsString(claimName);
    if (value == null || value.isBlank()) {
      throw new UnauthorizedException("Token inválido: claim obrigatória ausente: " + claimName);
    }
    return value;
  }
}
