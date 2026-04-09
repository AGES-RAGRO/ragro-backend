package br.com.ragro.controller.response;

import java.time.OffsetDateTime;
import java.util.UUID;

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
public class ProducerResponse {

  @Schema(
          description = "Unique identifier of the producer",
          example = "550e8400-e29b-41d4-a716-446655440000")
  private UUID id;

  @Schema(description = "Full name of the producer", example = "João Silva")
  private String name;

  @Schema(description = "Email address", example = "joao@example.com")
  private String email;

  @Schema(description = "Phone number", example = "(51) 98765-4321")
  private String phone;

  @Schema(description = "Account active status", example = "true")
  private boolean active;

  @Schema(description = "Account creation timestamp")
  private OffsetDateTime createdAt;

  @Schema(description = "Last account update timestamp")
  private OffsetDateTime updatedAt;
}
