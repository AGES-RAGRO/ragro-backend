package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Payload to update a producer profile")
public class ProducerUpdateRequest {

  @NotBlank(message = "Name is required")
  @Schema(
      description = "Full name of the producer",
      example = "Fazenda Paraíso",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  @NotBlank(message = "Phone is required")
  @Size(max = 20, message = "Phone must contain at most 20 characters")
  @Schema(
      description = "Phone number",
      example = "(51) 98765-4321",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String phone;
  
  @Schema(
      description = "Story or description of the producer/farm",
      example = "Nossa fazenda cultiva orgânicos há 10 anos...")
  private String story;

  @Schema(
      description = "URL to the producer's profile photo or logo",
      example = "https://example.com/photo.jpg")
  private String photoUrl;

  @Valid
  @NotNull(message = "Address is required")
  @Schema(
      description = "Address of the farm/producer",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private AddressRequest address;
}
