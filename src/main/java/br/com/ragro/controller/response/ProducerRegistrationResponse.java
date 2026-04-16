package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
@Schema(description = "Producer registration response")
public class ProducerRegistrationResponse {

    @Schema(description = "Unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Full name", example = "João Silva")
    private String name;

    @Schema(description = "Email address", example = "joao@example.com")
    private String email;

    @Schema(description = "Phone number", example = "(51) 98765-4321")
    private String phone;

    @Schema(description = "User type", example = "farmer")
    private String type;

    @Schema(description = "Account active status", example = "true")
    private boolean active;

    @Schema(description = "Fiscal number", example = "12345678901")
    private String fiscalNumber;

    @Schema(description = "Fiscal number type", example = "CPF")
    private String fiscalNumberType;

    @Schema(description = "Farm name", example = "Fazenda São João")
    private String farmName;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Avatar S3 URL")
    private String avatarS3;

    @Schema(description = "Display photo S3 URL")
    private String displayPhotoS3;

    @Schema(description = "Total reviews", example = "0")
    private Integer totalReviews;

    @Schema(description = "Average rating", example = "0.00")
    private BigDecimal averageRating;

    @Schema(description = "Total orders", example = "0")
    private Integer totalOrders;

    @Schema(description = "Total sales amount", example = "0.00")
    private BigDecimal totalSalesAmount;

    @Schema(description = "Account creation timestamp")
    private OffsetDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private OffsetDateTime updatedAt;
}