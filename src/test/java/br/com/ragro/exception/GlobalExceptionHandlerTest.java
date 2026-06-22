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
    // Dois POST /routes simultâneos: o INSERT perdedor viola uq_delivery_routes_farmer_active.
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
    // A mensagem crua do banco não vaza ao cliente.
    assertThat(response.getBody().getError()).doesNotContain("uq_delivery_routes_farmer_active");
  }
}
