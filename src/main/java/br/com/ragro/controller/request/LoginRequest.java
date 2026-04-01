package br.com.ragro.controller.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class LoginRequest {
    @NotBlank(message = "Email cannot be blank")
    private String email;
    
    @NotBlank(message = "Password cannot be blank")
    private String password;
}
