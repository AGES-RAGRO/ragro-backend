package br.com.ragro.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("RAGRO API")
                .version("1.0.0")
                .description(
                    "REST API for the RAGRO platform — connecting urban consumers with local family farmers")
                .contact(new Contact().name("RAGRO Team").url("https://github.com/AGES-RAGRO"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
        .servers(
            List.of(
                new Server().url("http://localhost:8080").description("Development Server"),
                new Server().url("https://api.ragro.com.br").description("Production Server")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearer-jwt",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token from AWS Cognito")
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization")));
  }
}
