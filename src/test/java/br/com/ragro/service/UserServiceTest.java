package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.api.IdentityProviderService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private IdentityProviderService identityProviderService;
  @Mock private Jwt jwt;

  @InjectMocks private UserService userService;

  // ─── getAuthenticatedUser ─────────────────────────────────────────────────

  @Test
  void getAuthenticatedUser_shouldReturnUser_whenFoundByAuthSub() {
    String sub = "keycloak-sub-abc";
    User expected = buildUser(sub);
    when(jwt.getClaimAsString("sub")).thenReturn(sub);
    when(jwt.getClaimAsString("email")).thenReturn("user@example.com");
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(expected));

    User result = userService.getAuthenticatedUser(jwt);

    assertThat(result).isSameAs(expected);
    verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void getAuthenticatedUser_shouldFallbackToEmail_andSelfHealAuthSub_whenSubNotFound() {
    String sub = "new-keycloak-sub";
    String email = "user@example.com";
    User existingUser = buildUser("old-sub");
    existingUser.setEmail(email);

    when(jwt.getClaimAsString("sub")).thenReturn(sub);
    when(jwt.getClaimAsString("email")).thenReturn(email);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.empty());
    when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
    when(userRepository.save(existingUser)).thenReturn(existingUser);

    User result = userService.getAuthenticatedUser(jwt);

    assertThat(result).isSameAs(existingUser);
    assertThat(existingUser.getAuthSub()).isEqualTo(sub);
    verify(userRepository).save(existingUser);
  }

  @Test
  void getAuthenticatedUser_shouldThrowUnauthorized_whenNeitherSubNorEmailMatch() {
    String sub = "unknown-sub";
    String email = "nobody@example.com";

    when(jwt.getClaimAsString("sub")).thenReturn(sub);
    when(jwt.getClaimAsString("email")).thenReturn(email);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.empty());
    when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getAuthenticatedUser(jwt))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Usuário não autenticado");
  }

  @Test
  void getAuthenticatedUser_shouldThrowUnauthorized_whenEmailIsBlankAndSubNotFound() {
    String sub = "unknown-sub";

    when(jwt.getClaimAsString("sub")).thenReturn(sub);
    when(jwt.getClaimAsString("email")).thenReturn("");
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getAuthenticatedUser(jwt))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Usuário não autenticado");

    verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void getAuthenticatedUser_shouldThrowUnauthorized_whenSubClaimIsMissing() {
    when(jwt.getClaimAsString("sub")).thenReturn(null);

    assertThatThrownBy(() -> userService.getAuthenticatedUser(jwt))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Token inválido");

    verify(userRepository, never()).findByAuthSub(org.mockito.ArgumentMatchers.any());
  }

  // ─── requireRole ──────────────────────────────────────────────────────────

  @Test
  void requireRole_shouldReturnUser_whenRoleMatches() {
    String sub = "sub-farmer";
    User farmer = buildUser(sub);
    farmer.setType(TypeUser.FARMER);
    when(jwt.getClaimAsString("sub")).thenReturn(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(farmer));

    User result = userService.requireRole(jwt, TypeUser.FARMER, "Apenas produtores");

    assertThat(result).isSameAs(farmer);
  }

  @Test
  void requireRole_shouldThrowForbidden_whenRoleDoesNotMatch() {
    String sub = "sub-customer";
    User customer = buildUser(sub);
    customer.setType(TypeUser.CUSTOMER);
    when(jwt.getClaimAsString("sub")).thenReturn(sub);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(customer));

    assertThatThrownBy(() -> userService.requireRole(jwt, TypeUser.FARMER, "Apenas produtores"))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Apenas produtores");
  }

  // ─── triggerPasswordReset ─────────────────────────────────────────────────

  @Test
  void triggerPasswordReset_shouldSendResetEmail_forTheTokenSubject() {
    String sub = "sub-reset";
    when(jwt.getClaimAsString("sub")).thenReturn(sub);

    userService.triggerPasswordReset(jwt);

    verify(identityProviderService).sendPasswordResetEmail(sub);
  }

  @Test
  void triggerPasswordReset_shouldThrowUnauthorized_whenSubClaimMissing() {
    when(jwt.getClaimAsString("sub")).thenReturn(null);

    assertThatThrownBy(() -> userService.triggerPasswordReset(jwt))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Token inválido");

    verify(identityProviderService, never()).sendPasswordResetEmail(org.mockito.ArgumentMatchers.any());
  }

  // ─── forgotPassword ───────────────────────────────────────────────────────

  @Test
  void forgotPassword_shouldSendResetEmail_whenUserExistsWithAuthSub() {
    String email = "user@example.com";
    User user = buildUser("sub-existing");
    when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

    userService.forgotPassword(email);

    verify(identityProviderService).sendPasswordResetEmail("sub-existing");
  }

  @Test
  void forgotPassword_shouldDoNothing_whenUserHasNoAuthSub() {
    String email = "user@example.com";
    User user = buildUser(null);
    when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

    userService.forgotPassword(email);

    verify(identityProviderService, never()).sendPasswordResetEmail(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void forgotPassword_shouldDoNothing_whenUserNotFound() {
    String email = "nobody@example.com";
    when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

    userService.forgotPassword(email);

    verify(identityProviderService, never()).sendPasswordResetEmail(org.mockito.ArgumentMatchers.any());
  }

  private User buildUser(String authSub) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test User");
    user.setEmail("user@example.com");
    user.setPhone("51999999999");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(true);
    user.setAuthSub(authSub);
    return user;
  }
}
