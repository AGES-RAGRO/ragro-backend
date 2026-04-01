package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Consumer (customer) registration response")
public class ConsumerRegistrationResponse {

    @Schema(description = "Unique identifier of the consumer account", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Full name of the consumer", example = "João Silva")
    private String name;

    @Schema(description = "Email address", example = "joao@example.com")
    private String email;

    @Schema(description = "Phone number", example = "(51) 98765-4321")
    private String phone;

    @Schema(description = "User type", example = "customer", allowableValues = {"admin", "customer", "farmer"})
    private String type;

    @Schema(description = "Account active status", example = "true")
    private boolean active;

    @Schema(description = "CPF (fiscal number)", example = "12345678901")
    private String fiscalNumber;

    @Schema(description = "Primary address")
    private AddressResponse address;

    @Schema(description = "Account creation timestamp")
    private OffsetDateTime createdAt;

    @Schema(description = "Last account update timestamp")
    private OffsetDateTime updatedAt;
}

