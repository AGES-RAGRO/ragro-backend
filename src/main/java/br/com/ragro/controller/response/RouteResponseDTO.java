package br.com.ragro.controller.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RouteResponseDTO {
  private double totalDistanceKm;
  private long totalDurationMins;
  private String overviewPolyline;
  private List<Integer> waypointOrder;
}
