package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Daily sales count for producer weekly chart")
public class ProducerWeeklySalesResponse {

  @Schema(description = "Actual date", example = "2026-03-01")
  private LocalDate date;

  @Schema(description = "Short day label", example = "seg.")
  private String dayLabel;

  @Schema(description = "Number of delivered orders for this day", example = "5")
  private long salesCount;
}