package br.com.ragro.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRequest {

  @NotBlank(message = "Name cannot be blank")
  private String name;

  @Email(message = "Email should be valid")
  private String email;

  private String phone;
}
