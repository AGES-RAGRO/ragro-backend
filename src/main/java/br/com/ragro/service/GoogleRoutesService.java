package br.com.ragro.service;

import br.com.ragro.exception.GoogleApiException;
import br.com.ragro.exception.GoogleApiException.Kind;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client for Google's <b>Routes API</b> (computeRoutes/computeRouteMatrix via REST). Field masks
 * limit the response to what we use (API requirement and cost control).
 */
@Service
@Slf4j
public class GoogleRoutesService {

  static final String ROUTES_FIELD_MASK =
      "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,"
          + "routes.legs.duration,routes.legs.distanceMeters,"
          + "routes.optimizedIntermediateWaypointIndex";
  static final String MATRIX_FIELD_MASK = "originIndex,destinationIndex,distanceMeters,condition";

  /** Documented limit of intermediates for computeRoutes with optimization. */
  public static final int MAX_INTERMEDIATE_WAYPOINTS = 25;

  /**
   * Total attempts (1 initial + 4 retries) for transient transport failures. The backoff window
   * (~1–2s) covers sporadic musl/Alpine DNS blips, which re-resolve per attempt thanks to
   * {@code -Dsun.net.inetaddr.negative.ttl=0}.
   */
  private static final int MAX_ATTEMPTS = 5;

  /** Backoff cap between retries, in ms. */
  private static final long BACKOFF_CAP_MILLIS = 1000;

  private final RestClient restClient;
  private final MeterRegistry meterRegistry;
  private final long retryBackoffMillis;

  public GoogleRoutesService(
      RestClient googleRoutesRestClient,
      MeterRegistry meterRegistry,
      @Value("${google.routes.retry.backoff-ms:200}") long retryBackoffMillis) {
    this.restClient = googleRoutesRestClient;
    this.meterRegistry = meterRegistry;
    this.retryBackoffMillis = retryBackoffMillis;
  }

  /** Simple geographic point (avoids depending on the legacy client's types). */
  public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {}

  /** Leg of the computed route: distance/duration to REACH the next point. */
  public record RouteLeg(BigDecimal distanceKm, int durationSeconds) {}

  /**
   * Optimized round-trip route: {@code optimizedOrder[i]} is the input-list index of the i-th stop to
   * visit; {@code legs} has N+1 entries (origin→p1, p1→p2, ..., pN→origin).
   */
  public record ComputedRoute(
      BigDecimal totalDistanceKm,
      int totalDurationSeconds,
      String encodedPolyline,
      List<Integer> optimizedOrder,
      List<RouteLeg> legs) {}

  /**
   * Computes the optimal route from {@code origin} through all {@code stops} and back (round trip —
   * product decision: consistent with the CO2 baseline of individual round trips), with live traffic.
   */
  public ComputedRoute computeOptimizedRoundTrip(GeoPoint origin, List<GeoPoint> stops) {
    if (stops.isEmpty()) {
      throw new GoogleApiException(Kind.INVALID_INPUT, "A rota precisa de ao menos uma parada");
    }
    if (stops.size() > MAX_INTERMEDIATE_WAYPOINTS) {
      throw new GoogleApiException(
          Kind.INVALID_INPUT,
          "A rota suporta no máximo " + MAX_INTERMEDIATE_WAYPOINTS + " paradas");
    }

    meterRegistry.counter("ragro.google.calls", "api", "routes").increment();

    var body = new java.util.LinkedHashMap<String, Object>();
    body.put("origin", waypoint(origin));
    body.put("destination", waypoint(origin));
    body.put("intermediates", stops.stream().map(this::waypoint).toList());
    body.put("travelMode", "DRIVE");
    body.put("routingPreference", "TRAFFIC_AWARE");
    body.put("optimizeWaypointOrder", true);

    ComputeRoutesResponse response;
    try {
      response =
          withRetry(
              () ->
                  restClient
                      .post()
                      .uri("/directions/v2:computeRoutes")
                      .header("X-Goog-FieldMask", ROUTES_FIELD_MASK)
                      .body(body)
                      .retrieve()
                      .body(ComputeRoutesResponse.class));
    } catch (RestClientResponseException e) {
      throw translate(e, "Falha ao calcular a rota");
    } catch (ResourceAccessException e) {
      // Transport failure (DNS/connection/timeout) persisting after retries — transient from the
      // client's view: respond 503 + Retry-After so it retries shortly.
      log.error("Routes API computeRoutes failed after {} attempts", MAX_ATTEMPTS, e);
      throw new GoogleApiException(
          Kind.TRANSIENT, "Serviço de rotas temporariamente indisponível", e);
    } catch (GoogleApiException e) {
      // Already classified (e.g. Kind.TRANSIENT from a withRetry backoff interrupt) — preserve.
      throw e;
    } catch (Exception e) {
      log.error("Routes API computeRoutes failed", e);
      throw new GoogleApiException(Kind.UNAVAILABLE, "Falha ao calcular a rota", e);
    }

    if (response == null || response.routes() == null || response.routes().isEmpty()) {
      throw new GoogleApiException(
          Kind.INVALID_INPUT, "Nenhuma rota encontrada para as paradas informadas");
    }

    Route route = response.routes().get(0);
    List<RouteLeg> legs =
        route.legs() == null
            ? List.of()
            : route.legs().stream()
                .map(leg -> new RouteLeg(metersToKm(leg.distanceMeters()), seconds(leg.duration())))
                .toList();
    // Routes API only returns optimizedIntermediateWaypointIndex as a valid permutation of [0, N)
    // with ≥2 intermediates; with 1 stop (or an unexpected response) it returns [-1]. Fall back to
    // natural order in that case.
    List<Integer> rawOrder = route.optimizedIntermediateWaypointIndex();
    List<Integer> optimizedOrder =
        isValidWaypointOrder(rawOrder, stops.size()) ? rawOrder : defaultOrder(stops.size());

    return new ComputedRoute(
        metersToKm(route.distanceMeters()),
        seconds(route.duration()),
        route.polyline() != null ? route.polyline().encodedPolyline() : null,
        optimizedOrder,
        legs);
  }

  /**
   * Road distance from origin to EACH stop (1 computeRouteMatrix call) — basis of the CO2 baseline
   * (individual round trips). Returns {@code null} on failure (caller uses haversine fallback); the
   * baseline must not break route creation.
   */
  public List<BigDecimal> distancesFromOrigin(GeoPoint origin, List<GeoPoint> stops) {
    meterRegistry.counter("ragro.google.calls", "api", "route_matrix").increment();
    var body = new java.util.LinkedHashMap<String, Object>();
    body.put("origins", List.of(java.util.Map.of("waypoint", waypoint(origin))));
    body.put("destinations", stops.stream().map(s -> java.util.Map.of("waypoint", waypoint(s))).toList());
    body.put("travelMode", "DRIVE");

    try {
      List<MatrixElement> elements =
          restClient
              .post()
              .uri("/distanceMatrix/v2:computeRouteMatrix")
              .header("X-Goog-FieldMask", MATRIX_FIELD_MASK)
              .body(body)
              .retrieve()
              .body(new org.springframework.core.ParameterizedTypeReference<>() {});
      if (elements == null) {
        return null;
      }
      BigDecimal[] byIndex = new BigDecimal[stops.size()];
      for (MatrixElement element : elements) {
        if (element.destinationIndex() != null
            && element.destinationIndex() >= 0
            && element.destinationIndex() < stops.size()
            && element.distanceMeters() != null) {
          byIndex[element.destinationIndex()] = metersToKm(element.distanceMeters());
        }
      }
      List<BigDecimal> distances = java.util.Arrays.asList(byIndex);
      return distances.contains(null) ? null : distances;
    } catch (Exception e) {
      log.warn("Route matrix failed; caller falls back to haversine baseline: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Retries {@code call} only on genuinely transient transport failures (DNS resolution, connection
   * refused) — e.g. EAI_AGAIN right after container start on Alpine/musl. {@code computeRoutes} is a
   * side-effect-free COMPUTE call (only billed), so retrying is safe. Does NOT retry HTTP errors
   * (arrive as {@link RestClientResponseException}) nor read-timeouts (request reached Google and was
   * likely billed — retrying would double the cost).
   */
  private <T> T withRetry(Supplier<T> call) {
    int attempt = 0;
    while (true) {
      attempt++;
      try {
        return call.get();
      } catch (ResourceAccessException e) {
        if (attempt >= MAX_ATTEMPTS || !isRetriableTransport(e)) {
          throw e;
        }
        meterRegistry.counter("ragro.google.routes.retries").increment();
        log.warn(
            "Routes API falha de transporte transitória (tentativa {}/{}): {} — reexecutando",
            attempt,
            MAX_ATTEMPTS,
            e.getMessage());
        sleepBackoff(attempt);
      }
    }
  }

  /** Only DNS/connection (never reached Google, not billed); read-timeout excluded. */
  private static boolean isRetriableTransport(ResourceAccessException e) {
    Throwable cause = e.getCause();
    return cause instanceof UnknownHostException || cause instanceof ConnectException;
  }

  /** Exponential backoff with full jitter (cap {@value #BACKOFF_CAP_MILLIS}ms); zero in tests. */
  private void sleepBackoff(int failedAttempt) {
    long ceiling = Math.min(BACKOFF_CAP_MILLIS, retryBackoffMillis * (1L << (failedAttempt - 1)));
    if (ceiling <= 0) {
      return;
    }
    long delay = ThreadLocalRandom.current().nextLong(ceiling + 1);
    if (delay == 0) {
      return;
    }
    try {
      Thread.sleep(delay);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new GoogleApiException(Kind.TRANSIENT, "Cálculo de rota interrompido", ie);
    }
  }

  private GoogleApiException translate(RestClientResponseException e, String safeMessage) {
    HttpStatusCode status = e.getStatusCode();
    // Technical detail logged only; Google's message never leaks to the client.
    log.error("Routes API error {}: {}", status.value(), e.getResponseBodyAsString());
    if (status.value() == 429) {
      return new GoogleApiException(Kind.QUOTA, "Serviço de rotas temporariamente indisponível");
    }
    if (status.is4xxClientError()) {
      return new GoogleApiException(Kind.INVALID_INPUT, safeMessage);
    }
    return new GoogleApiException(Kind.UNAVAILABLE, safeMessage);
  }

  private java.util.Map<String, Object> waypoint(GeoPoint point) {
    return java.util.Map.of(
        "location",
        java.util.Map.of(
            "latLng",
            java.util.Map.of(
                "latitude", point.latitude().doubleValue(),
                "longitude", point.longitude().doubleValue())));
  }

  private static BigDecimal metersToKm(Long meters) {
    if (meters == null) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(meters).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
  }

  /** Durations come as "1234s". */
  private static int seconds(String duration) {
    if (duration == null || duration.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(duration.replace("s", ""));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static List<Integer> defaultOrder(int size) {
    return java.util.stream.IntStream.range(0, size).boxed().toList();
  }

  /** Validates {@code order} is a permutation of [0, size) — otherwise natural order is used. */
  private static boolean isValidWaypointOrder(List<Integer> order, int size) {
    if (order == null || order.size() != size) {
      return false;
    }
    boolean[] seen = new boolean[size];
    for (Integer index : order) {
      if (index == null || index < 0 || index >= size || seen[index]) {
        return false;
      }
      seen[index] = true;
    }
    return true;
  }

  // ── Minimal response shapes (field mask guarantees only these fields) ──

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ComputeRoutesResponse(List<Route> routes) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Route(
      Long distanceMeters,
      String duration,
      Polyline polyline,
      List<Leg> legs,
      List<Integer> optimizedIntermediateWaypointIndex) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Polyline(String encodedPolyline) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Leg(Long distanceMeters, String duration) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record MatrixElement(Integer originIndex, Integer destinationIndex, Long distanceMeters) {}
}
