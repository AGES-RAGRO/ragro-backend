package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
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

  // ─── helpers ─────────────────────────────────────────────────────────────

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
