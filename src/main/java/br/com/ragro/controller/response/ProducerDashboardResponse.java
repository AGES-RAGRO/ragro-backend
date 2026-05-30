package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Producer financial dashboard for a specific month")
public class ProducerDashboardResponse {

  @Schema(description = "Selected month (1-12)", example = "3")
  private Integer month;

  @Schema(description = "Selected year", example = "2026")
  private Integer year;

  @Schema(description = "Total sales with comparison to previous month")
  private DashboardMetricResponse salesMetric;

  @Schema(description = "Delivered orders with comparison to previous month")
  private DashboardMetricResponse ordersMetric;

  @Schema(description = "Percentage of stock sold in the month (0-100)")
  private BigDecimal stockSoldPercentage;

  @Schema(description = "Stock sold percentage with comparison to previous month")
  private DashboardMetricResponse stockMetric;
}
