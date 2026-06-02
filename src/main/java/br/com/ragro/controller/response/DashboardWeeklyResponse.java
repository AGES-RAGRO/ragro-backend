package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
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
@Schema(description = "Producer dashboard with weekly sales data (last 7 days)")
public class DashboardWeeklyResponse {

  @Schema(description = "Start date of the week", example = "2026-03-01")
  private LocalDate weekStartDate;

  @Schema(description = "End date of the week", example = "2026-03-07")
  private LocalDate weekEndDate;

  @Schema(description = "Daily sales data for the last 7 days")
  private List<DailySalesResponse> dailySales;
}
