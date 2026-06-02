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
   * Comma-separated allowed Origin patterns. Accepts wildcards ({@code http://localhost:*}) and
   * full domains. In prod this must include the public API Gateway and frontend URLs.
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
    // API is a stateless bearer-token resource server (Authorization header, no cookies),
    // so credentialed CORS is unnecessary. Keeping it false avoids the wildcard-pattern +
    // allowCredentials combination that would let any matching origin make credentialed calls.
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
