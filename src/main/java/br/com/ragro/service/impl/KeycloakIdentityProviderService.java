package br.com.ragro.service.impl;

import br.com.ragro.exception.BusinessException;
import br.com.ragro.service.api.IdentityProviderService;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class KeycloakIdentityProviderService implements IdentityProviderService {

  private static final Logger log = LoggerFactory.getLogger(KeycloakIdentityProviderService.class);

  private final RestClient restClient;
  private final String serverUrl;
  private final String realm;
  private final String adminUsername;
  private final String adminPassword;

  public KeycloakIdentityProviderService(
      @Value("${keycloak.server-url}") String serverUrl,
      @Value("${keycloak.realm}") String realm,
      @Value("${keycloak.admin.username}") String adminUsername,
      @Value("${keycloak.admin.password}") String adminPassword) {
    this.restClient = RestClient.create();
    this.serverUrl = serverUrl;
    this.realm = realm;
    this.adminUsername = adminUsername;
    this.adminPassword = adminPassword;
  }

  @Override
  public String registerCustomer(String email, String rawPassword) {
    return registerUser(email, rawPassword, "CUSTOMER");
  }

  @Override
  public String registerProducer(String email, String rawPassword) {
    return registerUser(email, rawPassword, "FARMER");
  }

  @Override
  public void deleteUser(String userId) {
    try {
      String adminToken = getAdminToken();
      restClient
          .delete()
          .uri(serverUrl + "/admin/realms/" + realm + "/users/" + userId)
          .headers(h -> h.setBearerAuth(adminToken))
          .retrieve()
          .toBodilessEntity();
    } catch (Exception e) {
      log.error("Failed to delete Keycloak user {}: {}", userId, e.getMessage());
    }
  }

  private String registerUser(String email, String rawPassword, String group) {
    String adminToken = getAdminToken();
    String userId = createUser(adminToken, email, List.of(group));
    try {
      setPassword(adminToken, userId, rawPassword);
    } catch (Exception e) {
      log.error("Failed to set password for Keycloak user {}. Deleting orphaned user.", userId, e);
      deleteUser(userId);
      throw new BusinessException("Failed to complete user registration: " + e.getMessage());
    }
    return userId;
  }

  private String createUser(String adminToken, String email, List<String> groups) {
    Map<String, Object> userPayload = new HashMap<>();
    userPayload.put("username", email);
    userPayload.put("email", email);
    userPayload.put("enabled", true);
    userPayload.put("emailVerified", true);
    userPayload.put("requiredActions", List.of());
    userPayload.put("groups", groups);

    try {
      ResponseEntity<Void> response =
          restClient
              .post()
              .uri(serverUrl + "/admin/realms/" + realm + "/users")
              .headers(h -> h.setBearerAuth(adminToken))
              .contentType(MediaType.APPLICATION_JSON)
              .body(userPayload)
              .retrieve()
              .toBodilessEntity();

      URI location = response.getHeaders().getLocation();
      if (location == null) {
        throw new BusinessException("Keycloak did not return user location");
      }

      String path = location.getPath();
      return path.substring(path.lastIndexOf('/') + 1);

    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 409) {
        throw new BusinessException("E-mail already registered in identity provider");
      }
      throw new BusinessException(
          "Failed to register user in identity provider: " + e.getMessage());
    }
  }

  private void setPassword(String adminToken, String userId, String rawPassword) {
    Map<String, Object> credential = new HashMap<>();
    credential.put("type", "password");
    credential.put("value", rawPassword);
    credential.put("temporary", false);

    restClient
        .put()
        .uri(serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/reset-password")
        .headers(h -> h.setBearerAuth(adminToken))
        .contentType(MediaType.APPLICATION_JSON)
        .body(credential)
        .retrieve()
        .toBodilessEntity();
  }

  private String getAdminToken() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("client_id", "admin-cli");
    form.add("username", adminUsername);
    form.add("password", adminPassword);

    @SuppressWarnings("unchecked")
    Map<String, Object> tokenResponse =
        restClient
            .post()
            .uri(serverUrl + "/realms/master/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);

    if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
      throw new BusinessException("Failed to obtain Keycloak admin token");
    }

    return (String) tokenResponse.get("access_token");
  }
}
