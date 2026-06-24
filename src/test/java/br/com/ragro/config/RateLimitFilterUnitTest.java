package br.com.ragro.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Pure unit test for {@link RateLimitFilter} (no Spring context). Drives {@code doFilterInternal}
 * directly with Mock servlet objects and a fresh filter instance per test so each test owns its own
 * Caffeine bucket cache. Limits are kept tiny (2/min) so the N+1 call trips the limiter
 * deterministically without waiting on token refill.
 */
class RateLimitFilterUnitTest {

  private RateLimitProperties properties;
  private RateLimitFilter filter;
  private MockHttpServletResponse response;
  private FilterChain chain;

  @BeforeEach
  void setUp() {
    properties = new RateLimitProperties();
    // Tiny limits so the 3rd call (N+1) is blocked.
    properties.setRegisterPerMinute(2);
    properties.setForgotPerMinute(2);
    properties.setRecommendationsPerMinute(2);
    properties.setRoutesPerMinute(2);
    // LocalDateTime in ErrorResponse requires the JSR-310 module (same as the production
    // ObjectMapper); a bare ObjectMapper would fail to serialize the 429 body.
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    filter = new RateLimitFilter(properties, objectMapper);
    response = new MockHttpServletResponse();
    chain = mock(FilterChain.class);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ---------------------------------------------------------------------------
  // enabled flag
  // ---------------------------------------------------------------------------

  @Test
  void doFilterInternal_whenDisabled_passesThroughWithoutStatus() throws Exception {
    properties.setEnabled(false);
    MockHttpServletRequest req = post("/auth/register/customer");

    filter.doFilterInternal(req, response, chain);

    verify(chain).doFilter(req, response);
    // Disabled means no rate-limit machinery runs: status stays the MockResponse default (200),
    // never 429.
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(response.getHeader("Retry-After")).isNull();
  }

  @Test
  void doFilterInternal_whenEnabledAndWithinLimit_passesThrough() throws Exception {
    properties.setEnabled(true);
    MockHttpServletRequest req = post("/auth/register/customer");

    filter.doFilterInternal(req, response, chain);

    verify(chain).doFilter(req, response);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  // ---------------------------------------------------------------------------
  // no matching rule -> passthrough
  // ---------------------------------------------------------------------------

  @Test
  void doFilterInternal_whenNoRuleMatches_passesThrough() throws Exception {
    MockHttpServletRequest req = get("/something");

    filter.doFilterInternal(req, response, chain);

    verify(chain).doFilter(req, response);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(response.getHeader("Retry-After")).isNull();
  }

  @Test
  void doFilterInternal_registerWithWrongMethod_doesNotMatchRule() throws Exception {
    // GET (not POST) on /auth/register/** must NOT match the register rule -> passthrough.
    MockHttpServletRequest req = get("/auth/register/customer");

    filter.doFilterInternal(req, response, chain);

    verify(chain).doFilter(req, response);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void doFilterInternal_recommendationsWithWrongMethod_doesNotMatchRule() throws Exception {
    // POST (not GET) on /recommendations must NOT match -> passthrough, no limiting.
    MockHttpServletRequest req = post("/recommendations");
    req.setRemoteAddr("9.9.9.9");

    for (int i = 0; i < 5; i++) {
      MockHttpServletResponse resp = new MockHttpServletResponse();
      filter.doFilterInternal(req, resp, chain);
      assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK.value());
    }
    // 5 calls all passed through despite limit of 2 -> rule did not match.
    verify(chain, times(5)).doFilter(eqReq(req), anyResp());
  }

  // ---------------------------------------------------------------------------
  // each rule matches and is limited
  // ---------------------------------------------------------------------------

  @Test
  void doFilterInternal_registerRule_limitsPerIpAfterCapacity() throws Exception {
    assertLimitedRule(post("/auth/register/customer"), "1.1.1.1");
  }

  @Test
  void doFilterInternal_forgotRule_limitsPerIpAfterCapacity() throws Exception {
    assertLimitedRule(post("/auth/password/forgot"), "2.2.2.2");
  }

  @Test
  void doFilterInternal_recommendationsRule_limitsPerUserAfterCapacity() throws Exception {
    setAuthenticatedJwt("user-rec");
    assertLimitedRule(get("/recommendations"), null);
  }

  @Test
  void doFilterInternal_routesExactPath_limitsAfterCapacity() throws Exception {
    setAuthenticatedJwt("user-routes-exact");
    assertLimitedRule(get("/routes"), null);
  }

  @Test
  void doFilterInternal_routesSubPath_limitsAfterCapacity() throws Exception {
    setAuthenticatedJwt("user-routes-sub");
    assertLimitedRule(get("/routes/xyz"), null);
  }

  /**
   * Drives the given request capacity times (all pass) then one more time (blocked), asserting both
   * the allowed and the blocked outcome with distinct expected values.
   */
  private void assertLimitedRule(MockHttpServletRequest req, String remoteAddr) throws Exception {
    if (remoteAddr != null) {
      req.setRemoteAddr(remoteAddr);
    }
    FilterChain localChain = mock(FilterChain.class);

    // Capacity = 2: first two calls pass.
    for (int i = 0; i < 2; i++) {
      MockHttpServletResponse resp = new MockHttpServletResponse();
      filter.doFilterInternal(req, resp, localChain);
      assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK.value());
    }
    verify(localChain, times(2)).doFilter(eqReq(req), anyResp());

    // 3rd call (N+1) is blocked.
    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilterInternal(req, blocked, localChain);

    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(blocked.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    assertThat(blocked.getCharacterEncoding()).isEqualTo("UTF-8");
    assertThat(blocked.getHeader("Retry-After")).isNotNull();
    assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isGreaterThanOrEqualTo(1);
    assertThat(blocked.getContentAsString())
        .contains("Muitas requisições")
        .contains("\"status\":429")
        .contains(req.getRequestURI());
    // chain.doFilter still only called twice -> NOT called for the blocked request.
    verify(localChain, times(2)).doFilter(eqReq(req), anyResp());
  }

  // ---------------------------------------------------------------------------
  // 429 body / headers detail (single explicit assertion path)
  // ---------------------------------------------------------------------------

  @Test
  void doFilterInternal_whenLimitExceeded_setsAllResponseFieldsAndSkipsChain() throws Exception {
    MockHttpServletRequest req = post("/auth/register/customer");
    req.setRemoteAddr("3.3.3.3");
    req.setRequestURI("/auth/register/customer");

    // Exhaust capacity (2).
    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    verify(chain, times(2)).doFilter(eqReq(req), anyResp());

    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilterInternal(req, blocked, chain);

    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(blocked.getContentType()).startsWith("application/json");
    assertThat(blocked.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
    assertThat(blocked.getHeader("Retry-After")).isNotNull();
    assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isGreaterThanOrEqualTo(1);
    assertThat(blocked.getContentAsString())
        .contains("Muitas requisições. Tente novamente em instantes.")
        .contains("\"status\":429")
        .contains("\"path\":\"/auth/register/customer\"");
    // Blocked request must NOT advance the chain (still only the 2 allowed calls).
    verify(chain, times(2)).doFilter(eqReq(req), anyResp());
  }

  // ---------------------------------------------------------------------------
  // resolveKey: per-IP via X-Forwarded-For LAST hop vs remoteAddr
  // ---------------------------------------------------------------------------

  @Test
  void resolveKey_perIp_usesForwardedForLastHop_independentBuckets() throws Exception {
    // Client A: forwarded chain ending in "c" -> keyed by "c".
    MockHttpServletRequest reqA = post("/auth/register/customer");
    reqA.setRemoteAddr("10.0.0.1");
    reqA.addHeader("X-Forwarded-For", "a, b, c");

    // Exhaust A's bucket (capacity 2) and block the 3rd.
    filter.doFilterInternal(reqA, new MockHttpServletResponse(), chain);
    filter.doFilterInternal(reqA, new MockHttpServletResponse(), chain);
    MockHttpServletResponse blockedA = new MockHttpServletResponse();
    filter.doFilterInternal(reqA, blockedA, chain);
    assertThat(blockedA.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // Client B: different last hop "z" -> independent bucket, still allowed.
    MockHttpServletRequest reqB = post("/auth/register/customer");
    reqB.setRemoteAddr("10.0.0.1");
    reqB.addHeader("X-Forwarded-For", "a, b, z");
    MockHttpServletResponse okB = new MockHttpServletResponse();
    filter.doFilterInternal(reqB, okB, chain);
    assertThat(okB.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void resolveKey_perIp_fallsBackToRemoteAddr_whenNoForwardedHeader() throws Exception {
    // Two requests with the SAME remoteAddr and no XFF share one bucket.
    MockHttpServletRequest first = post("/auth/register/customer");
    first.setRemoteAddr("192.168.0.5");
    MockHttpServletRequest second = post("/auth/register/customer");
    second.setRemoteAddr("192.168.0.5");
    MockHttpServletRequest third = post("/auth/register/customer");
    third.setRemoteAddr("192.168.0.5");

    filter.doFilterInternal(first, new MockHttpServletResponse(), chain);
    filter.doFilterInternal(second, new MockHttpServletResponse(), chain);
    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilterInternal(third, blocked, chain);

    // Shared bucket -> 3rd blocked.
    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // A different remoteAddr is a different bucket -> allowed.
    MockHttpServletRequest other = post("/auth/register/customer");
    other.setRemoteAddr("192.168.0.6");
    MockHttpServletResponse okOther = new MockHttpServletResponse();
    filter.doFilterInternal(other, okOther, chain);
    assertThat(okOther.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void resolveKey_perIp_blankForwardedFor_fallsBackToRemoteAddr() throws Exception {
    // Blank XFF must be ignored (isBlank branch) and remoteAddr used instead.
    MockHttpServletRequest req = post("/auth/register/customer");
    req.setRemoteAddr("172.16.0.9");
    req.addHeader("X-Forwarded-For", "   ");

    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilterInternal(req, blocked, chain);
    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // Same remoteAddr also keyed via fallback -> a fresh request to 172.16.0.10 is allowed.
    MockHttpServletRequest other = post("/auth/register/customer");
    other.setRemoteAddr("172.16.0.10");
    other.addHeader("X-Forwarded-For", "");
    MockHttpServletResponse ok = new MockHttpServletResponse();
    filter.doFilterInternal(other, ok, chain);
    assertThat(ok.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  // ---------------------------------------------------------------------------
  // resolveKey: per-user (recommendations/routes)
  // ---------------------------------------------------------------------------

  @Test
  void resolveKey_perUser_keyedByPrincipal_jwtAuthentication() throws Exception {
    // User Alice exhausts her own bucket.
    setAuthenticatedJwt("alice");
    MockHttpServletRequest reqAlice = get("/recommendations");
    reqAlice.setRemoteAddr("55.55.55.55");
    filter.doFilterInternal(reqAlice, new MockHttpServletResponse(), chain);
    filter.doFilterInternal(reqAlice, new MockHttpServletResponse(), chain);
    MockHttpServletResponse blockedAlice = new MockHttpServletResponse();
    filter.doFilterInternal(reqAlice, blockedAlice, chain);
    assertThat(blockedAlice.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // Bob (different principal) shares the SAME IP but has an independent bucket -> allowed.
    setAuthenticatedJwt("bob");
    MockHttpServletRequest reqBob = get("/recommendations");
    reqBob.setRemoteAddr("55.55.55.55");
    MockHttpServletResponse okBob = new MockHttpServletResponse();
    filter.doFilterInternal(reqBob, okBob, chain);
    assertThat(okBob.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void resolveKey_perUser_keyedByPrincipal_usernamePasswordAuthentication() throws Exception {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            "carol",
            "n/a",
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(auth);

    MockHttpServletRequest req = get("/recommendations");
    req.setRemoteAddr("66.66.66.66");
    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilterInternal(req, blocked, chain);
    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // Different authenticated user, same IP -> independent bucket, allowed.
    UsernamePasswordAuthenticationToken auth2 =
        new UsernamePasswordAuthenticationToken(
            "dave", "n/a", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(auth2);
    MockHttpServletRequest req2 = get("/recommendations");
    req2.setRemoteAddr("66.66.66.66");
    MockHttpServletResponse ok = new MockHttpServletResponse();
    filter.doFilterInternal(req2, ok, chain);
    assertThat(ok.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void resolveKey_perUser_anonymousFallsBackToIp() throws Exception {
    // AnonymousAuthenticationToken must be treated like "no user" -> per-IP keying.
    AnonymousAuthenticationToken anon =
        new AnonymousAuthenticationToken(
            "anonKey",
            "anonymousUser",
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    SecurityContextHolder.getContext().setAuthentication(anon);

    // Same IP across calls -> shared IP bucket -> 3rd blocked.
    MockHttpServletRequest req = get("/recommendations");
    req.setRemoteAddr("77.77.77.77");
    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilterInternal(req, blocked, chain);
    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // Same anonymous principal but different IP -> different bucket -> allowed
    // (proves keying was per-IP, not per the constant "anonymousUser" name).
    MockHttpServletRequest reqOtherIp = get("/recommendations");
    reqOtherIp.setRemoteAddr("88.88.88.88");
    MockHttpServletResponse ok = new MockHttpServletResponse();
    filter.doFilterInternal(reqOtherIp, ok, chain);
    assertThat(ok.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void resolveKey_perUser_noAuthentication_fallsBackToIp() throws Exception {
    // No SecurityContext authentication at all -> per-IP keying.
    SecurityContextHolder.clearContext();

    MockHttpServletRequest req = get("/recommendations");
    req.setRemoteAddr("99.99.99.99");
    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilterInternal(req, blocked, chain);
    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    MockHttpServletRequest other = get("/recommendations");
    other.setRemoteAddr("99.99.99.100");
    MockHttpServletResponse ok = new MockHttpServletResponse();
    filter.doFilterInternal(other, ok, chain);
    assertThat(ok.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void resolveKey_perUser_unauthenticatedToken_fallsBackToIp() throws Exception {
    // A token whose isAuthenticated() is false must fall back to per-IP keying.
    UsernamePasswordAuthenticationToken unauth =
        new UsernamePasswordAuthenticationToken("eve", "creds");
    assertThat(unauth.isAuthenticated()).isFalse();
    SecurityContextHolder.getContext().setAuthentication(unauth);

    MockHttpServletRequest req = get("/recommendations");
    req.setRemoteAddr("100.100.100.1");
    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilterInternal(req, blocked, chain);
    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // Different IP -> allowed (proves IP keying, not "eve" user keying).
    MockHttpServletRequest other = get("/recommendations");
    other.setRemoteAddr("100.100.100.2");
    MockHttpServletResponse ok = new MockHttpServletResponse();
    filter.doFilterInternal(other, ok, chain);
    assertThat(ok.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------

  private static MockHttpServletRequest post(String uri) {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setMethod("POST");
    req.setRequestURI(uri);
    return req;
  }

  private static MockHttpServletRequest get(String uri) {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setMethod("GET");
    req.setRequestURI(uri);
    return req;
  }

  private static void setAuthenticatedJwt(String sub) {
    Jwt jwt =
        Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .claim("sub", sub)
            .subject(sub)
            .build();
    // 3-arg ctor: marks the token authenticated (single-arg leaves it unauthenticated, which would
    // make the filter fall back to per-IP keying) and pins getName() to the sub for per-user keying.
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of(), sub));
  }

  private static MockHttpServletRequest eqReq(MockHttpServletRequest req) {
    return org.mockito.ArgumentMatchers.eq(req);
  }

  private static MockHttpServletResponse anyResp() {
    return org.mockito.ArgumentMatchers.any(MockHttpServletResponse.class);
  }
}
