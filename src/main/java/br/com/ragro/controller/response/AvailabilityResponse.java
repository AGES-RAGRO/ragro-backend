package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Producer availability slot")
public class AvailabilityResponse {

  @Schema(description = "Weekday (0=sunday ... 6=saturday)", example = "1")
  private Integer weekday;

  @Schema(description = "Opening time (HH:mm)", example = "08:00")
  private String opensAt;

  @Schema(description = "Closing time (HH:mm)", example = "18:00")
  private String closesAt;
}
