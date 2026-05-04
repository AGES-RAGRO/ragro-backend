package br.com.ragro.controller.response;

import java.util.List;
import java.util.Map;

public record DeliveryRouteResponse(
    String routeId,
    int totalDistanceMeters,
    int totalDurationSeconds,
    List<DeliveryRouteStopResponse> stops,
    Map<String, Object> geometry) {}
