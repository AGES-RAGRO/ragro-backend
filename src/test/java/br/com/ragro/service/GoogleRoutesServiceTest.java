package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.ragro.exception.GoogleApiException;
import br.com.ragro.service.GoogleRoutesService.ComputedRoute;
import br.com.ragro.service.GoogleRoutesService.GeoPoint;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleRoutesServiceTest {

  private MockRestServiceServer server;
  private GoogleRoutesService service;

  private final GeoPoint origin = new GeoPoint(BigDecimal.valueOf(-30.0), BigDecimal.valueOf(-51.0));
  private final List<GeoPoint> stops =
      List.of(
          new GeoPoint(BigDecimal.valueOf(-30.1), BigDecimal.valueOf(-51.1)),
          new GeoPoint(BigDecimal.valueOf(-30.2), BigDecimal.valueOf(-51.2)));

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    service =
        new GoogleRoutesService(builder, "test-key", "https://routes.test", new SimpleMeterRegistry());
  }

  @Test
  void computeOptimizedRoundTrip_shouldParseRouteWithLegsAndOptimizedOrder() {
    server
        .expect(requestTo("https://routes.test/directions/v2:computeRoutes"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Goog-Api-Key", "test-key"))
        .andExpect(header("X-Goog-FieldMask", GoogleRoutesService.ROUTES_FIELD_MASK))
        .andRespond(
            withSuccess(
                """
                {"routes":[{
                  "distanceMeters": 12500,
                  "duration": "1800s",
                  "polyline": {"encodedPolyline": "abc123"},
                  "optimizedIntermediateWaypointIndex": [1, 0],
                  "legs": [
                    {"distanceMeters": 5000, "duration": "600s"},
                    {"distanceMeters": 4000, "duration": "500s"},
                    {"distanceMeters": 3500, "duration": "700s"}
                  ]
                }]}
                """,
                MediaType.APPLICATION_JSON));

    ComputedRoute route = service.computeOptimizedRoundTrip(origin, stops);

    assertThat(route.totalDistanceKm()).isEqualByComparingTo("12.50");
    assertThat(route.totalDurationSeconds()).isEqualTo(1800);
    assertThat(route.encodedPolyline()).isEqualTo("abc123");
    assertThat(route.optimizedOrder()).containsExactly(1, 0);
    assertThat(route.legs()).hasSize(3);
    assertThat(route.legs().get(0).durationSeconds()).isEqualTo(600);
  }

  @Test
  void computeOptimizedRoundTrip_shouldThrowQuota_whenGoogleReturns429() {
    server
        .expect(requestTo("https://routes.test/directions/v2:computeRoutes"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> service.computeOptimizedRoundTrip(origin, stops))
        .isInstanceOf(GoogleApiException.class)
        .extracting(e -> ((GoogleApiException) e).getKind())
        .isEqualTo(GoogleApiException.Kind.QUOTA);
  }

  @Test
  void computeOptimizedRoundTrip_shouldThrowInvalidInput_whenGoogleReturns400() {
    server
        .expect(requestTo("https://routes.test/directions/v2:computeRoutes"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> service.computeOptimizedRoundTrip(origin, stops))
        .isInstanceOf(GoogleApiException.class)
        .extracting(e -> ((GoogleApiException) e).getKind())
        .isEqualTo(GoogleApiException.Kind.INVALID_INPUT);
  }

  @Test
  void computeOptimizedRoundTrip_shouldRejectTooManyStops_withoutCallingGoogle() {
    List<GeoPoint> tooMany =
        java.util.stream.IntStream.range(0, 26)
            .mapToObj(i -> new GeoPoint(BigDecimal.valueOf(-30.0 - i), BigDecimal.valueOf(-51)))
            .toList();

    assertThatThrownBy(() -> service.computeOptimizedRoundTrip(origin, tooMany))
        .isInstanceOf(GoogleApiException.class)
        .hasMessageContaining("no máximo");
  }

  @Test
  void distancesFromOrigin_shouldParseMatrixByDestinationIndex() {
    server
        .expect(requestTo("https://routes.test/distanceMatrix/v2:computeRouteMatrix"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                [
                  {"originIndex": 0, "destinationIndex": 1, "distanceMeters": 6000},
                  {"originIndex": 0, "destinationIndex": 0, "distanceMeters": 5000}
                ]
                """,
                MediaType.APPLICATION_JSON));

    List<BigDecimal> distances = service.distancesFromOrigin(origin, stops);

    assertThat(distances).hasSize(2);
    assertThat(distances.get(0)).isEqualByComparingTo("5.00");
    assertThat(distances.get(1)).isEqualByComparingTo("6.00");
  }

  @Test
  void distancesFromOrigin_shouldReturnNull_whenGoogleFails() {
    server
        .expect(requestTo("https://routes.test/distanceMatrix/v2:computeRouteMatrix"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThat(service.distancesFromOrigin(origin, stops)).isNull();
  }
}
