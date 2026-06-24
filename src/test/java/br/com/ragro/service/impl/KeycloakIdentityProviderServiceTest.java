package br.com.ragro.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.InternalServerException;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests the Keycloak admin REST adapter against a {@link MockRestServiceServer}, asserting the
 * actual requests it issues (URLs, payloads) and how it maps Keycloak responses to domain
 * exceptions. The internally-created {@code RestClient} is swapped for a server-bound one via
 * reflection so no production wiring changes are needed.
 */
class KeycloakIdentityProviderServiceTest {

  private static final String SERVER = "https://kc.test";
  private static final String REALM = "ragro";
  private static final String TOKEN_URL =
      SERVER + "/realms/master/protocol/openid-connect/token";
  private static final String USERS_URL = SERVER + "/admin/realms/" + REALM + "/users";
  private static final String USER_ID = "USER-123";
  private static final String USER_URL = USERS_URL + "/" + USER_ID;
  private static final String TOKEN_JSON = "{\"access_token\":\"admin-tok\"}";

  private MockRestServiceServer server;
  private KeycloakIdentityProviderService service;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    service = new KeycloakIdentityProviderService(SERVER, REALM, "admin", "secret");
    ReflectionTestUtils.setField(service, "restClient", builder.build());
  }

  private void expectAdminToken(ExpectedCount count) {
    server
        .expect(count, requestTo(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        // The admin-cli password grant must carry all four form fields.
        .andExpect(content().string(containsString("grant_type=password")))
        .andExpect(content().string(containsString("client_id=admin-cli")))
        .andExpect(content().string(containsString("username=admin")))
        .andExpect(content().string(containsString("password=secret")))
        .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
  }

  // ─── registerCustomer / registerProducer ──────────────────────────────────

  @Test
  void registerCustomer_happyPath_createsUserSetsPasswordAndReturnsId() {
    expectAdminToken(ExpectedCount.once());
    server
        .expect(requestTo(USERS_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer admin-tok"))
        .andExpect(jsonPath("$.email").value("buyer@ragro.test"))
        .andExpect(jsonPath("$.groups[0]").value("CUSTOMER"))
        .andRespond(withCreatedEntity(URI.create(USER_URL)));
    server
        .expect(requestTo(USER_URL + "/reset-password"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header("Authorization", "Bearer admin-tok"))
        .andExpect(jsonPath("$.value").value("s3cret"))
        .andRespond(withSuccess());

    String id = service.registerCustomer("buyer@ragro.test", "s3cret");

    assertThat(id).isEqualTo(USER_ID);
    server.verify();
  }

  @Test
  void registerProducer_sendsFarmerGroup() {
    expectAdminToken(ExpectedCount.once());
    server
        .expect(requestTo(USERS_URL))
        .andExpect(jsonPath("$.groups[0]").value("FARMER"))
        .andRespond(withCreatedEntity(URI.create(USER_URL)));
    server.expect(requestTo(USER_URL + "/reset-password")).andRespond(withSuccess());

    assertThat(service.registerProducer("farmer@ragro.test", "pw")).isEqualTo(USER_ID);
  }

  @Test
  void registerUser_whenEmailAlreadyExists_throwsBusinessException() {
    expectAdminToken(ExpectedCount.once());
    server.expect(requestTo(USERS_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

    assertThatThrownBy(() -> service.registerCustomer("dupe@ragro.test", "pw"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("already registered");
  }

  @Test
  void registerUser_whenCreateFailsWithOtherStatus_throwsGenericBusinessException() {
    expectAdminToken(ExpectedCount.once());
    server.expect(requestTo(USERS_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> service.registerCustomer("x@ragro.test", "pw"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Failed to register user");
  }

  @Test
  void registerUser_whenNoLocationHeader_throwsBusinessException() {
    expectAdminToken(ExpectedCount.once());
    server.expect(requestTo(USERS_URL)).andRespond(withStatus(HttpStatus.CREATED));

    assertThatThrownBy(() -> service.registerCustomer("x@ragro.test", "pw"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("did not return user location");
  }

  @Test
  void registerUser_whenSetPasswordFails_deletesOrphanAndThrows() {
    // token is requested twice: once for the registration, once inside the compensating delete.
    expectAdminToken(ExpectedCount.manyTimes());
    server.expect(requestTo(USERS_URL)).andRespond(withCreatedEntity(URI.create(USER_URL)));
    server
        .expect(requestTo(USER_URL + "/reset-password"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    server
        .expect(requestTo(USER_URL))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withSuccess());

    assertThatThrownBy(() -> service.registerCustomer("x@ragro.test", "pw"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Failed to complete user registration");
    server.verify();
  }

  // ─── getAdminToken failure modes ──────────────────────────────────────────

  @Test
  void registerUser_whenAdminTokenCallFails_throwsInternalServerException() {
    server
        .expect(requestTo(TOKEN_URL))
        .andRespond(withException(new IOException("connection refused")));

    assertThatThrownBy(() -> service.registerCustomer("x@ragro.test", "pw"))
        .isInstanceOf(InternalServerException.class)
        .hasMessageContaining("provedor de identidade");
  }

  @Test
  void registerUser_whenTokenResponseHasNoAccessToken_throwsInternalServerException() {
    server
        .expect(requestTo(TOKEN_URL))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> service.registerCustomer("x@ragro.test", "pw"))
        .isInstanceOf(InternalServerException.class)
        .hasMessageContaining("Failed to obtain Keycloak admin token");
  }

  // ─── sendPasswordResetEmail ───────────────────────────────────────────────

  @Test
  void sendPasswordResetEmail_happyPath_callsExecuteActionsEmail() {
    expectAdminToken(ExpectedCount.once());
    server
        .expect(requestTo(USER_URL + "/execute-actions-email"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header("Authorization", "Bearer admin-tok"))
        .andExpect(jsonPath("$[0]").value("UPDATE_PASSWORD"))
        .andRespond(withSuccess());

    assertThatCode(() -> service.sendPasswordResetEmail(USER_ID)).doesNotThrowAnyException();
    server.verify();
  }

  @Test
  void sendPasswordResetEmail_whenCallFails_throwsBusinessException() {
    expectAdminToken(ExpectedCount.once());
    server
        .expect(requestTo(USER_URL + "/execute-actions-email"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> service.sendPasswordResetEmail(USER_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("redefinição de senha");
  }

  // ─── deleteUser (best-effort, never throws) ───────────────────────────────

  @Test
  void deleteUser_happyPath_issuesDelete() {
    expectAdminToken(ExpectedCount.once());
    server
        .expect(requestTo(USER_URL))
        .andExpect(method(HttpMethod.DELETE))
        .andExpect(header("Authorization", "Bearer admin-tok"))
        .andRespond(withSuccess());

    assertThatCode(() -> service.deleteUser(USER_ID)).doesNotThrowAnyException();
    server.verify();
  }

  @Test
  void deleteUser_swallowsErrors() {
    expectAdminToken(ExpectedCount.once());
    server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    // Best-effort cleanup: a failed delete must not propagate.
    assertThatCode(() -> service.deleteUser(USER_ID)).doesNotThrowAnyException();
  }
}
