package br.com.ragro.exception;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ragro.controller.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void dataIntegrityViolation_shouldReturn409Conflict_notRaw500() {
    // Two concurrent POST /routes: the losing INSERT violates uq_delivery_routes_farmer_active.
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/routes");
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException(
            "could not execute statement",
            new RuntimeException(
                "duplicate key value violates unique constraint"
                    + " \"uq_delivery_routes_farmer_active\""));

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody().getError()).contains("Tente novamente");
    assertThat(response.getBody().getPath()).isEqualTo("/routes");
    // Raw DB message does not leak to the client.
    assertThat(response.getBody().getError()).doesNotContain("uq_delivery_routes_farmer_active");
  }

  @Test
  void googleApiTransient_shouldReturn503_withRetryAfter() {
    // Transient transport failure (DNS/connection) to Google, persisting after retries.
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/routes");
    GoogleApiException ex =
        new GoogleApiException(
            GoogleApiException.Kind.TRANSIENT, "Serviço de rotas temporariamente indisponível");

    ResponseEntity<ErrorResponse> response = handler.handleGoogleApi(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
    assertThat(response.getBody().getError())
        .isEqualTo("Serviço de rotas temporariamente indisponível");
  }

  @Test
  void googleApiUnavailable_shouldReturn500_withoutRetryAfter() {
    // Non-transient error (key/contract/unexpected) stays 500, no retry suggested.
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/routes");
    GoogleApiException ex =
        new GoogleApiException(GoogleApiException.Kind.UNAVAILABLE, "Falha ao calcular a rota");

    ResponseEntity<ErrorResponse> response = handler.handleGoogleApi(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getHeaders().getFirst("Retry-After")).isNull();
  }
}
