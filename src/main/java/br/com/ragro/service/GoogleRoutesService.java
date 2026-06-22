package br.com.ragro.service;

import br.com.ragro.exception.GoogleApiException;
import br.com.ragro.exception.GoogleApiException.Kind;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente da <b>Routes API</b> nova do Google (computeRoutes/computeRouteMatrix via REST) — a
 * Directions API usada antes é legada no pricing e não expunha trânsito nem ETA por leg no fluxo
 * antigo. Field masks limitam a resposta ao que usamos (requisito da API e controle de custo).
 */
@Service
@Slf4j
public class GoogleRoutesService {

  static final String ROUTES_FIELD_MASK =
      "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,"
          + "routes.legs.duration,routes.legs.distanceMeters,"
          + "routes.optimizedIntermediateWaypointIndex";
  static final String MATRIX_FIELD_MASK = "originIndex,destinationIndex,distanceMeters,condition";

  /** Limite documentado de intermediates do computeRoutes com otimização. */
  public static final int MAX_INTERMEDIATE_WAYPOINTS = 25;

  private final RestClient restClient;
  private final MeterRegistry meterRegistry;

  public GoogleRoutesService(
      RestClient.Builder restClientBuilder,
      @Value("${google.maps.api-key}") String apiKey,
      @Value("${google.routes.base-url:https://routes.googleapis.com}") String baseUrl,
      MeterRegistry meterRegistry) {
    this.restClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("X-Goog-Api-Key", apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    this.meterRegistry = meterRegistry;
  }

  /** Ponto geográfico simples (evita depender dos tipos do client legado). */
  public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {}

  /** Leg da rota computada: distância/duração para CHEGAR ao próximo ponto. */
  public record RouteLeg(BigDecimal distanceKm, int durationSeconds) {}

  /**
   * Rota otimizada round-trip: {@code optimizedOrder[i]} é o índice (na lista de entrada) da
   * i-ésima parada a visitar; {@code legs} tem N+1 entradas (origem→p1, p1→p2, ..., pN→origem).
   */
  public record ComputedRoute(
      BigDecimal totalDistanceKm,
      int totalDurationSeconds,
      String encodedPolyline,
      List<Integer> optimizedOrder,
      List<RouteLeg> legs) {}

  /**
   * Calcula a rota ótima saindo de {@code origin}, passando por todas as {@code stops} e
   * retornando à origem (round trip — decisão de produto: consistente com o baseline de CO2 que
   * compara com idas-e-voltas individuais), com trânsito do momento.
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
          restClient
              .post()
              .uri("/directions/v2:computeRoutes")
              .header("X-Goog-FieldMask", ROUTES_FIELD_MASK)
              .body(body)
              .retrieve()
              .body(ComputeRoutesResponse.class);
    } catch (RestClientResponseException e) {
      throw translate(e, "Falha ao calcular a rota");
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
    List<Integer> optimizedOrder =
        route.optimizedIntermediateWaypointIndex() != null
            ? route.optimizedIntermediateWaypointIndex()
            : defaultOrder(stops.size());

    return new ComputedRoute(
        metersToKm(route.distanceMeters()),
        seconds(route.duration()),
        route.polyline() != null ? route.polyline().encodedPolyline() : null,
        optimizedOrder,
        legs);
  }

  /**
   * Distância rodoviária da origem a CADA parada (1 chamada computeRouteMatrix) — base do CO2
   * baseline (ida-e-volta individual), substituindo a linha reta (haversine) usada antes, que
   * tornava a comparação maçãs-com-laranjas. Retorna {@code null} em falha (chamador usa
   * fallback) — baseline não pode derrubar a criação da rota.
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

  private GoogleApiException translate(RestClientResponseException e, String safeMessage) {
    HttpStatusCode status = e.getStatusCode();
    // Detalhe técnico só no log; a mensagem do Google não vaza para o cliente.
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

  /** Durations vêm como "1234s". */
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

  // ── Shapes mínimos da resposta (field mask garante só estes campos) ──

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
