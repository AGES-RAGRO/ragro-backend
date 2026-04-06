package br.com.ragro.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class ActiveUserFilterTest {

  @Mock private UserRepository userRepository;

  private ActiveUserFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private FilterChain filterChain;

  @BeforeEach
  void setUp() {
    filter = new ActiveUserFilter(userRepository);
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    filterChain = mock(FilterChain.class);
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldContinueChain_whenRequestIsNotAuthenticated() throws Exception {
    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  void shouldContinueChain_whenUserIsActive() throws Exception {
    String sub = "keycloak-sub-123";
    setAuthenticatedJwt(sub);

    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  void shouldReturn403_whenUserIsInactive() throws Exception {
    String sub = "keycloak-sub-456";
    setAuthenticatedJwt(sub);

    User inactiveUser = buildUser(sub, false);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(inactiveUser));

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain, never()).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(response.getContentAsString()).contains("Conta desativada");
  }

  @Test
  void shouldReturn403_whenUserNotFoundInDatabase() throws Exception {
    String sub = "keycloak-sub-789";
    setAuthenticatedJwt(sub);

    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.empty());

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain, never()).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(response.getContentAsString()).contains("Conta desativada");
  }

  @Test
  void shouldStoreUserInRequestAttribute_whenUserIsActive() throws Exception {
    String sub = "keycloak-sub-active";
    setAuthenticatedJwt(sub);

    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    filter.doFilterInternal(request, response, filterChain);

    assertThat(request.getAttribute("authenticatedUser")).isEqualTo(activeUser);
  }

  private void setAuthenticatedJwt(String sub) {
    Jwt jwt =
        Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .claim("sub", sub)
            .claim("email", "test@example.com")
            .build();
    JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private User buildUser(String authSub, boolean active) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test User");
    user.setEmail("test@example.com");
    user.setPhone("51999999999");
    user.setType(TypeUser.FARMER);
    user.setActive(active);
    user.setAuthSub(authSub);
    user.setCreatedAt(OffsetDateTime.now().minusDays(1));
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }
}
