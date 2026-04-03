package br.com.ragro.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Value("${keycloak.public-url:http://localhost:8180}")
  private String keycloakPublicUrl;

  @Value("${keycloak.realm:ragro}")
  private String realm;

  @Bean
  public OpenAPI customOpenAPI() {
    String tokenUrl = keycloakPublicUrl + "/realms/" + realm + "/protocol/openid-connect/token";

    return new OpenAPI()
        .info(
            new Info()
                .title("RAGRO API")
                .version("1.0.0")
                .description(
                    "REST API for the RAGRO platform — connecting urban customers with local family"
                        + " farmers")
                .contact(new Contact().name("RAGRO Team").url("https://github.com/AGES-RAGRO"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
        .servers(
            List.of(
                new Server().url("http://localhost:8080").description("Development Server"),
                new Server().url("https://api.ragro.com.br").description("Production Server")))
        .tags(
            List.of(
                new Tag()
                    .name("Authentication")
                    .description("User registration and authentication"),
                new Tag()
                    .name("Admin")
                    .description("Administrative operations (requires ROLE_ADMIN)"),
                new Tag()
                    .name("Customers")
                    .description("Customer operations (requires ROLE_CUSTOMER)"),
                new Tag()
                    .name("Producer")
                    .description("Producer operations (requires ROLE_FARMER)")))
        .addSecurityItem(new SecurityRequirement().addList("keycloak-oauth"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "keycloak-oauth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .description(
                            "Keycloak OAuth2 — insira username e password para obter token")
                        .flows(new OAuthFlows().password(new OAuthFlow().tokenUrl(tokenUrl)))));
  }
}
