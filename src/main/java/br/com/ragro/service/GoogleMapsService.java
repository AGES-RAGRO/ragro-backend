package br.com.ragro.service;

import br.com.ragro.controller.request.RouteRequestDTO;
import br.com.ragro.controller.response.RouteResponseDTO;
import br.com.ragro.exception.BusinessException;
import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GoogleMapsService {

  private final GeoApiContext context;

  public GoogleMapsService(@Value("${google.maps.api-key}") String apiKey) {
    this.context = new GeoApiContext.Builder().apiKey(apiKey).build();
  }

  public LatLng geocodeAddress(String address) {
    try {
      GeocodingResult[] results = GeocodingApi.geocode(context, address).await();
      if (results != null && results.length > 0) {
        return results[0].geometry.location;
      }
    } catch (Exception e) {
      log.error("Failed to geocode address: {}", address, e);
    }
    return null;
  }

  public RouteResponseDTO optimizeRoute(RouteRequestDTO request) {
    try {
      DirectionsApiRequest apiRequest =
          DirectionsApi.newRequest(context)
              .origin(request.getOrigin())
              .destination(request.getDestination());

      if (request.getWaypoints() != null && !request.getWaypoints().isEmpty()) {
        String[] waypointsArray = request.getWaypoints().toArray(new String[0]);
        apiRequest.waypoints(waypointsArray);
        apiRequest.optimizeWaypoints(true);
      }

      DirectionsResult result = apiRequest.await();

      if (result.routes == null || result.routes.length == 0) {
        throw new BusinessException("Nenhuma rota encontrada para os pontos fornecidos.");
      }

      DirectionsRoute route = result.routes[0];

      long totalDistanceMeters = 0;
      long totalDurationSeconds = 0;

      if (route.legs != null) {
        for (var leg : route.legs) {
          if (leg.distance != null) {
            totalDistanceMeters += leg.distance.inMeters;
          }
          if (leg.duration != null) {
            totalDurationSeconds += leg.duration.inSeconds;
          }
        }
      }

      double totalDistanceKm = totalDistanceMeters / 1000.0;
      long totalDurationMins = totalDurationSeconds / 60;

      String polyline =
          route.overviewPolyline != null ? route.overviewPolyline.getEncodedPath() : null;

      var waypointOrder =
          route.waypointOrder != null
              ? Arrays.stream(route.waypointOrder).boxed().collect(Collectors.toList())
              : null;

      return RouteResponseDTO.builder()
          .totalDistanceKm(totalDistanceKm)
          .totalDurationMins(totalDurationMins)
          .overviewPolyline(polyline)
          .waypointOrder(waypointOrder)
          .build();

    } catch (Exception e) {
      log.error("Erro ao otimizar rota com Google Maps", e);
      throw new BusinessException("Falha ao comunicar com o Google Maps: " + e.getMessage());
    }
  }
}
