package br.com.ragro.controller.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Fix #2: {@link CreateRouteRequest} bounds latitude to [-90, 90] and longitude to [-180, 180] via
 * {@code @DecimalMin}/{@code @DecimalMax}, so absurd coordinates are rejected at the edge instead of
 * being sent to the routing API.
 */
class CreateRouteRequestValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  private CreateRouteRequest request(BigDecimal lat, BigDecimal lng) {
    CreateRouteRequest request = new CreateRouteRequest();
    request.setOriginLatitude(lat);
    request.setOriginLongitude(lng);
    return request;
  }

  @Test
  void validCoordinates_produceNoViolations() {
    Set<ConstraintViolation<CreateRouteRequest>> violations =
        validator.validate(request(new BigDecimal("-30.0346"), new BigDecimal("-51.2177")));
    assertThat(violations).isEmpty();
  }

  @Test
  void boundaryCoordinates_areAccepted() {
    assertThat(validator.validate(request(new BigDecimal("90.0"), new BigDecimal("180.0"))))
        .isEmpty();
    assertThat(validator.validate(request(new BigDecimal("-90.0"), new BigDecimal("-180.0"))))
        .isEmpty();
  }

  @Test
  void latitudeOutOfRange_producesViolation() {
    Set<ConstraintViolation<CreateRouteRequest>> violations =
        validator.validate(request(new BigDecimal("999"), new BigDecimal("0")));
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getPropertyPath().toString().equals("originLatitude"));
  }

  @Test
  void longitudeOutOfRange_producesViolation() {
    Set<ConstraintViolation<CreateRouteRequest>> violations =
        validator.validate(request(new BigDecimal("0"), new BigDecimal("999")));
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getPropertyPath().toString().equals("originLongitude"));
  }

  @Test
  void bothOutOfRange_produceTwoViolations() {
    Set<ConstraintViolation<CreateRouteRequest>> violations =
        validator.validate(request(new BigDecimal("999"), new BigDecimal("999")));
    assertThat(violations).hasSize(2);
  }
}
