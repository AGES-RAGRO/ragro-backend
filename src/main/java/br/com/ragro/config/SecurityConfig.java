package br.com.ragro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

  /**
   * Public endpoints, handled by a chain without the OAuth2 resource server so a presented token
   * is ignored (a stale token must not 401 a {@code permitAll} route). Not {@code /co2/**}: {@code
   * POST /co2/record-savings} is authenticated.
   */
  private static final String[] PUBLIC_MATCHERS = {
    "/actuator/health",
    "/media/**",
    "/auth/register/customer",
    "/auth/password/forgot",
    "/auth/config",
    "/v3/api-docs",
    "/v3/api-docs/**",
    "/swagger-ui",
    "/swagger-ui.html",
    "/swagger-ui/**",
    "/swagger-resources",
    "/swagger-resources/**",
    "/webjars/**",
    "/co2/options",
    "/co2/total-saved",
    "/co2/calculate"
  };

  @Bean
  @Order(1)
  public SecurityFilterChain publicSecurityFilterChain(
      HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
    return http.securityMatcher(PUBLIC_MATCHERS)
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .build();
  }

  @Bean
  @Order(2)
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
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/search")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.POST, "/reviews")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/producers/locations")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/producers")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/producers/*/profile")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/producers/*/products")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/producers/*/products/*")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/producers/*/reviews")
                    .hasAnyRole("CUSTOMER", "FARMER")
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
