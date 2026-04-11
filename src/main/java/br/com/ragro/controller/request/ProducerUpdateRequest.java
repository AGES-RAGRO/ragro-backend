package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@Schema(description = "Payload to update the authenticated producer profile")
public class ProducerUpdateRequest {

  @Size(max = 120, message = "Name must contain at most 120 characters")
  @Schema(description = "Full name of the producer", example = "João Silva")
  private String name;

  @Size(max = 20, message = "Phone must contain at most 20 characters")
  @Schema(description = "Phone number", example = "(51) 98765-4321")
  private String phone;

  @Size(max = 150, message = "Farm name must contain at most 150 characters")
  @Schema(description = "Name of the farm", example = "Fazenda São João")
  private String farmName;

  @Schema(description = "Description of the farm")
  private String description;

  @Schema(description = "Avatar S3 URL")
  private String avatarS3;

  @Schema(description = "Display photo S3 URL")
  private String displayPhotoS3;

  @Schema(description = "Producer's personal story")
  private String story;

  @Schema(description = "Public profile photo URL")
  private String photoUrl;

  @Schema(description = "Date when the producer became a member", example = "2020-03-15")
  private LocalDate memberSince;

  @Valid
  @Schema(description = "Primary address of the producer")
  private AddressRequest address;

  @Valid
  @Schema(description = "Payment method data (pix or bank_account). Upserts the active record of the given type.")
  private PaymentMethodRequest paymentMethod;
}
