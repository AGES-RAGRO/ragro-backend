package br.com.ragro.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

  /**
   * Lista de patterns permitidos como Origin separados por vírgula. Aceita curingas
   * ({@code http://localhost:*}) e domínios completos. Em dev pode ficar só com localhost; em
   * prod deve incluir a URL pública do API Gateway e a URL do frontend.
   */
  @Value("${cors.allowed-origin-patterns:http://localhost:*}")
  private String allowedOriginPatterns;

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    List<String> patterns =
        Arrays.stream(allowedOriginPatterns.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
    configuration.setAllowedOriginPatterns(patterns);

    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization"));
    configuration.setExposedHeaders(Arrays.asList("Authorization"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
