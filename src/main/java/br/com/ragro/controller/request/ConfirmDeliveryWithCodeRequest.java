package br.com.ragro.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConfirmDeliveryWithCodeRequest {

  @NotBlank(message = "O código de confirmação é obrigatório")
  @Size(min = 4, max = 4, message = "O código de confirmação deve ter exatamente 4 dígitos")
  @Pattern(regexp = "\\d{4}", message = "O código de confirmação deve conter apenas dígitos")
  private String code;
}
