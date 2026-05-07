package br.com.ragro.service;

import br.com.ragro.controller.request.UpdateUserRequest;
import br.com.ragro.domain.User;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.api.IdentityProviderService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final IdentityProviderService identityProviderService;

  public UserService(UserRepository userRepository, IdentityProviderService identityProviderService) {
    this.userRepository = userRepository;
    this.identityProviderService = identityProviderService;
  }

  /**
   * Resolves the authenticated user from JWT claims.
   * Lookup strategy (D6):
   * 1. Try findByAuthSub(sub)
   * 2. If not found, try findByEmail(email) and self-heal auth_sub
   * 3. If neither matches, throw UnauthorizedException
   */
  @Transactional
  public User getAuthenticatedUser(Jwt jwt) {
    String sub = getRequiredClaim(jwt, "sub");
    String email = jwt.getClaimAsString("email");

    return userRepository.findByAuthSub(sub)
        .orElseGet(() -> {
          if (email == null || email.isBlank()) {
            throw new UnauthorizedException("Usuário não autenticado");
          }
          User user = userRepository.findByEmail(email)
              .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));
          // Self-heal: update auth_sub so future lookups hit the fast path
          user.setAuthSub(sub);
          return userRepository.save(user);
        });
  }

  @Transactional
  public User updateUser(User user, UpdateUserRequest request) {
    user.setName(request.getName().trim());
    if (request.getPhone() != null) {
      user.setPhone(request.getPhone().trim());
    }
    return userRepository.saveAndFlush(user);
  }

  @Transactional
  public void triggerPasswordReset(Jwt jwt) {
    String sub = getRequiredClaim(jwt, "sub");
    identityProviderService.sendPasswordResetEmail(sub);
  }

  @Transactional(readOnly = true)
  public void forgotPassword(String email) {
    userRepository.findByEmail(email).ifPresent(user -> {
      if (user.getAuthSub() != null) {
        identityProviderService.sendPasswordResetEmail(user.getAuthSub());
      }
    });
  }

  public String getRequiredClaim(Jwt jwt, String claimName) {
    String value = jwt.getClaimAsString(claimName);
    if (value == null || value.isBlank()) {
      throw new UnauthorizedException("Token inválido: claim obrigatória ausente: " + claimName);
    }
    return value;
  }
}
