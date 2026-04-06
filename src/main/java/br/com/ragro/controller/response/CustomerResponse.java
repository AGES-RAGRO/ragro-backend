package br.com.ragro.controller.response;

import lombok.*;


import java.time.OffsetDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
public class CustomerResponse {

    private UUID id;
    private String name;
    private String email;
    private String phone;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
@Schema(description = "Customer profile response")
public class CustomerResponse {

  @Schema(
      description = "Unique identifier of the customer",
      example = "550e8400-e29b-41d4-a716-446655440000")
  private UUID id;

  @Schema(description = "Full name of the customer", example = "João Silva")
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

  @Schema(description = "List of customer addresses")
  private List<AddressResponse> addresses;
}
