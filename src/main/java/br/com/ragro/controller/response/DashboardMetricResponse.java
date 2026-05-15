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
@Schema(description = "A metric with comparison to previous period")
public class DashboardMetricResponse {

  @Schema(description = "Current period value", example = "128")
  private BigDecimal currentValue;

  @Schema(description = "Percentage change compared to previous period", example = "+12.5")
  private BigDecimal percentageChange;

  @Schema(description = "Previous period value", example = "113.78")
  private BigDecimal previousValue;
}

