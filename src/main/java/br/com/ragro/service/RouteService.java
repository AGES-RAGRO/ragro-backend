package br.com.ragro.service;

import br.com.ragro.controller.response.DeliveryRouteResponse;
import br.com.ragro.controller.response.DeliveryRouteStopResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.AddressSnapshot;
import br.com.ragro.domain.Coordinates;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.OrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteService {

  private static final double AVG_SPEED_MS = 30_000.0 / 3600.0; // 30 km/h in m/s

  private final JdbcTemplate jdbcTemplate;
  private final OrderRepository orderRepository;
  private final AddressRepository addressRepository;
  private final GeocodingService geocodingService;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public DeliveryRouteResponse calculateTodayRoute(UUID farmerId) {
    verifyOsmDataLoaded();

    List<Order> todayOrders = orderRepository
        .findByFarmerIdAndStatus(farmerId, OrderStatus.IN_DELIVERY)
        .stream()
        .filter(o -> o.getCreatedAt().toLocalDate().equals(LocalDate.now()))
        .toList();

    if (todayOrders.isEmpty()) {
      return new DeliveryRouteResponse(null, 0, 0, List.of(), null);
    }

    Address origin = addressRepository
        .findByUserIdAndIsPrimaryTrue(farmerId)
        .orElseThrow(() -> new NotFoundException("Producer has no primary address"));

    if (origin.getLatitude() == null || origin.getLongitude() == null) {
      throw new BusinessException(
          "Producer origin address has no coordinates. Run POST /admin/geocode-batch first.");
    }

    List<StopPoint> stops = buildStopPoints(todayOrders);
    if (stops.isEmpty()) {
      return new DeliveryRouteResponse(null, 0, 0, List.of(), null);
    }

    long originVertex = findNearestVertex(
        origin.getLatitude().doubleValue(), origin.getLongitude().doubleValue());

    List<Long> vertices = new ArrayList<>();
    vertices.add(originVertex);
    for (StopPoint stop : stops) {
      vertices.add(findNearestVertex(stop.latitude, stop.longitude));
    }

    RouteResult result = calculateRoute(vertices);

    List<DeliveryRouteStopResponse> stopResponses = new ArrayList<>();
    for (int i = 0; i < stops.size(); i++) {
      StopPoint s = stops.get(i);
      stopResponses.add(new DeliveryRouteStopResponse(
          i + 1, s.orderId, s.formattedAddress, s.latitude, s.longitude));
    }

    int durationSeconds = (int) (result.totalDistanceMeters / AVG_SPEED_MS);

    return new DeliveryRouteResponse(
        UUID.randomUUID().toString(),
        result.totalDistanceMeters,
        durationSeconds,
        stopResponses,
        result.geoJson);
  }

  private List<StopPoint> buildStopPoints(List<Order> orders) {
    List<StopPoint> stops = new ArrayList<>();
    for (Order order : orders) {
      AddressSnapshot snap = order.getDeliveryAddressSnapshot();
      if (snap == null) continue;

      double lat;
      double lon;

      if (snap.getLatitude() != null && snap.getLongitude() != null) {
        lat = snap.getLatitude().doubleValue();
        lon = snap.getLongitude().doubleValue();
      } else {
        // Snapshot has no coords — try real-time geocoding
        Coordinates coords = geocodingService
            .geocodeSnapshot(snap.getNumber(), snap.getStreet(), snap.getCity(), snap.getState())
            .orElse(null);
        if (coords == null) {
          log.warn("Skipping order {} — could not geocode delivery address", order.getId());
          continue;
        }
        lat = coords.latitude();
        lon = coords.longitude();
      }

      String formatted = snap.getStreet() + ", " + snap.getNumber()
          + " — " + snap.getNeighborhood()
          + ", " + snap.getCity() + " – " + snap.getState();

      stops.add(new StopPoint(order.getId().toString(), formatted, lat, lon));
    }
    return stops;
  }

  private long findNearestVertex(double lat, double lon) {
    Long id = jdbcTemplate.queryForObject(
        "SELECT id FROM ways_vertices_pgr ORDER BY the_geom <-> ST_SetSRID(ST_Point(?, ?), 4326) LIMIT 1",
        Long.class, lon, lat);
    if (id == null) {
      throw new BusinessException("No road network vertex found near coordinates.");
    }
    return id;
  }

  private RouteResult calculateRoute(List<Long> orderedVertices) {
    if (orderedVertices.size() < 2) {
      return new RouteResult(0, null);
    }

    List<String> segmentGeoms = new ArrayList<>();
    int totalDistance = 0;

    for (int i = 0; i < orderedVertices.size() - 1; i++) {
      long src = orderedVertices.get(i);
      long dst = orderedVertices.get(i + 1);

      String sql = """
          SELECT
            ST_AsGeoJSON(ST_LineMerge(ST_Collect(w.the_geom))) AS geom,
            COALESCE(SUM(w.length_m), 0)::int AS dist_m
          FROM pgr_dijkstra(
            'SELECT gid AS id, source, target, cost FROM ways WHERE cost > 0',
            ?, ?, false
          ) AS r
          JOIN ways w ON r.edge = w.gid
          WHERE r.edge != -1
          """;

      Map<String, Object> row = jdbcTemplate.queryForMap(sql, src, dst);
      String geomJson = (String) row.get("geom");
      int dist = ((Number) row.get("dist_m")).intValue();

      if (geomJson != null) {
        segmentGeoms.add(geomJson);
      }
      totalDistance += dist;
    }

    if (segmentGeoms.isEmpty()) {
      return new RouteResult(totalDistance, null);
    }

    Map<String, Object> geoJson = mergeLineStrings(segmentGeoms, totalDistance);
    return new RouteResult(totalDistance, geoJson);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mergeLineStrings(List<String> segmentGeoms, int totalDistance) {
    // Build a GeoJSON LineString by collecting all coordinate arrays from each segment
    List<List<Double>> allCoords = new ArrayList<>();
    for (String geomJson : segmentGeoms) {
      try {
        Map<String, Object> geom = objectMapper.readValue(geomJson, new TypeReference<>() {});
        String type = (String) geom.get("type");
        if ("LineString".equals(type)) {
          List<List<Double>> coords = (List<List<Double>>) geom.get("coordinates");
          if (allCoords.isEmpty()) {
            allCoords.addAll(coords);
          } else {
            // Skip first coord of subsequent segments to avoid duplicates at junctions
            allCoords.addAll(coords.subList(1, coords.size()));
          }
        } else if ("MultiLineString".equals(type)) {
          List<List<List<Double>>> multiCoords = (List<List<List<Double>>>) geom.get("coordinates");
          for (List<List<Double>> line : multiCoords) {
            if (allCoords.isEmpty()) {
              allCoords.addAll(line);
            } else {
              allCoords.addAll(line.subList(1, line.size()));
            }
          }
        }
      } catch (Exception e) {
        log.warn("Failed to parse segment geometry: {}", e.getMessage());
      }
    }

    Map<String, Object> result = new HashMap<>();
    result.put("type", "LineString");
    result.put("coordinates", allCoords);
    return result;
  }

  private void verifyOsmDataLoaded() {
    try {
      jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ways", Integer.class);
    } catch (Exception e) {
      throw new BusinessException(
          "Road network data not loaded. Run scripts/load-osm-data.sh first.");
    }
  }

  private record StopPoint(String orderId, String formattedAddress, double latitude, double longitude) {}

  private record RouteResult(int totalDistanceMeters, Map<String, Object> geoJson) {}
}
