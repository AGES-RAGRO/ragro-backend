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
@Schema(description = "Public producer profile visible to customers")
public class ProducerPublicProfileResponse {

  private UUID id;

  @Schema(description = "Full name of the producer", example = "João Silva")
  private String name;

  @Schema(description = "Name of the farm", example = "Fazenda São João")
  private String farmName;

  @Schema(description = "Description of the farm")
  private String description;

  @Schema(description = "Producer's personal story")
  private String story;

  @Schema(description = "Public profile photo URL")
  private String photoUrl;

  @Schema(description = "Avatar S3 URL")
  private String avatarS3;

  @Schema(description = "Cover/background photo S3 URL")
  private String displayPhotoS3;

  @Schema(description = "Contact phone number", example = "(51) 98765-4321")
  private String phone;

  @Schema(description = "Average rating", example = "4.80")
  private BigDecimal averageRating;

  @Schema(description = "Total number of reviews", example = "42")
  private Integer totalReviews;

  @Schema(description = "Date when the producer became a member", example = "2020-03-15")
  private LocalDate memberSince;

  @Schema(description = "Primary address / location")
  private AddressResponse address;

  @Schema(description = "Public producer availability slots")
  private List<AvailabilityResponse> availability;
}
