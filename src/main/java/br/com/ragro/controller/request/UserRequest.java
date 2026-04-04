package br.com.ragro.controller.request;

import br.com.ragro.domain.enums.TypeUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRequest {

  @NotBlank(message = "Name cannot be blank")
  private String name;

  @Email(message = "Email should be valid")
  private String email;

  @Pattern(regexp = "^\\d{11}$", message = "Phone must contain 11 digits (DDD + number)")
  private String phone;

  @NotNull(message = "User type is required")
  private TypeUser type;
}
