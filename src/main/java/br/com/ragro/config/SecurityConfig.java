package br.com.ragro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CorsConfigurationSource corsConfigurationSource,
      JwtAuthenticationConverter jwtAuthenticationConverter,
      ActiveUserFilter activeUserFilter)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(HttpMethod.GET, "/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/register/customer")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/password/forgot")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/auth/config")
                    .permitAll()
                    .requestMatchers("/v3/api-docs", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/swagger-ui", "/swagger-ui.html", "/swagger-ui/**")
                    .permitAll()
                    .requestMatchers("/swagger-resources", "/swagger-resources/**")
                    .permitAll()
                    .requestMatchers("/webjars/**")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/producers")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/producers/*/profile")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/producers/*/products")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/producers/*/products/*")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/producers/stock/*/movements")
                    .hasRole("FARMER")
                    .requestMatchers("/producers/**")
                    .hasRole("FARMER")
                    .requestMatchers("/customers/**")
                    .hasRole("CUSTOMER")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .addFilterAfter(activeUserFilter, BearerTokenAuthenticationFilter.class)
        .build();
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter(
      KeycloakRolesConverter keycloakRolesConverter) {
    JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
    scopesConverter.setAuthorityPrefix("SCOPE_");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        new DelegatingJwtGrantedAuthoritiesConverter(scopesConverter, keycloakRolesConverter));
    return converter;
  }
}
