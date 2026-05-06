package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
@Schema(description = "Standard API error response")
public class ErrorResponse {

  @Schema(description = "Error timestamp", example = "2026-05-06T18:30:00")
  private LocalDateTime timestamp;
  @Schema(description = "HTTP status code", example = "404")
  private int status;
  @Schema(description = "Human-readable error message", example = "Producer not found")
  private String error;
  @Schema(
      description = "Request path that produced the error",
      example = "/producers/550e8400-e29b-41d4-a716-446655440000/reviews")
  private String path;
}
