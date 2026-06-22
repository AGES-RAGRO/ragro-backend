package br.com.ragro.mapper;

import br.com.ragro.controller.response.RouteResponse;
import br.com.ragro.controller.response.RouteStopResponse;
import br.com.ragro.domain.DeliveryRoute;
import br.com.ragro.domain.RouteStop;
import java.util.Comparator;

public class DeliveryRouteMapper {

  private DeliveryRouteMapper() {}

  public static RouteResponse toResponse(DeliveryRoute route) {
    if (route == null) {
      return null;
    }
    return RouteResponse.builder()
        .id(route.getId())
        .status(route.getStatus())
        .originLatitude(route.getOriginLatitude())
        .originLongitude(route.getOriginLongitude())
        .totalDistanceKm(route.getTotalDistanceKm())
        .totalDurationSeconds(route.getTotalDurationSeconds())
        .baselineDistanceKm(route.getBaselineDistanceKm())
        .overviewPolyline(route.getOverviewPolyline())
        .createdAt(route.getCreatedAt())
        .completedAt(route.getCompletedAt())
        .stops(
            route.getStops().stream()
                .sorted(Comparator.comparingInt(RouteStop::getSequence))
                .map(DeliveryRouteMapper::toStopResponse)
                .toList())
        .build();
  }

  private static RouteStopResponse toStopResponse(RouteStop stop) {
    return RouteStopResponse.builder()
        .id(stop.getId())
        .orderId(stop.getOrder().getId())
        .sequence(stop.getSequence())
        .status(stop.getStatus())
        .latitude(stop.getLatitude())
        .longitude(stop.getLongitude())
        .addressText(stop.getAddressText())
        .customerName(stop.getOrder().getCustomer().getUser().getName())
        .legDistanceKm(stop.getLegDistanceKm())
        .legDurationSeconds(stop.getLegDurationSeconds())
        .eta(stop.getEta())
        .completedAt(stop.getCompletedAt())
        .build();
  }
}
