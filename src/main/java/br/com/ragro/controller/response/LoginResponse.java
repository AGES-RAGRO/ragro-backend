package br.com.ragro.controller.response;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private String token;  
    private UserDTO user;
    
    @Data
    public static class UserDTO {
        private String id;
        private String name;
        private String email;
        private String phone;
        private String type;  
        private boolean active;
    }
}