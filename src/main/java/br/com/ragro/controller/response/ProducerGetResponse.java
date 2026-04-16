package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
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
@Schema(description = "Full producer profile response")
public class ProducerGetResponse {

  private UUID id;

  @Schema(description = "Full name", example = "João Silva")
  private String name;

  @Schema(description = "Email address", example = "joao@example.com")
  private String email;

  @Schema(description = "Phone number", example = "(51) 98765-4321")
  private String phone;

  @Schema(description = "CPF or CNPJ (digits only)", example = "12345678901")
  private String fiscalNumber;

  @Schema(description = "Fiscal number type", example = "CPF")
  private String fiscalNumberType;

  @Schema(description = "Name of the farm", example = "Fazenda São João")
  private String farmName;

  @Schema(description = "Description of the farm")
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

  @Schema(description = "Producer's personal story")
  private String story;

  @Schema(description = "Public profile photo URL")
  private String photoUrl;

  @Schema(description = "Date when the producer became a member", example = "2020-03-15")
  private LocalDate memberSince;

  @Schema(description = "Whether the producer is active", example = "true")
  private Boolean active;

  @Schema(description = "Primary address")
  private AddressResponse address;

  @Schema(description = "Active payment methods")
  private List<PaymentMethodResponse> paymentMethods;

  @Schema(description = "Producer availability (service hours per weekday)")
  private List<AvailabilityResponse> availability;
}

