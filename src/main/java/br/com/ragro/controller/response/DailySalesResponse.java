package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
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
@Schema(description = "Daily sales data")
public class DailySalesResponse {

  @Schema(description = "Day of the week (full name)", example = "Monday")
  private String dayOfWeek;

  @Schema(description = "Date in format dd/MM", example = "01/03")
  private String date;

  @Schema(description = "Actual date", example = "2026-03-01")
  private LocalDate fullDate;

  @Schema(description = "Number of orders for this day", example = "5")
  private Long orderCount;

  @Schema(description = "Total sales amount for this day", example = "450.50")
  private BigDecimal salesAmount;
}

