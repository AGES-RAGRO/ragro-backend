package br.com.ragro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
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
            CognitoLogoutHandler cognitoLogoutHandler
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(authorize -> authorize
                        // Public endpoints
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/login/**").permitAll()
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        
                        // Admin-only endpoints
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        
                        // Farmer-only endpoints
                        .requestMatchers("/farmer/**").hasRole("FARMER")
                        
                        // Customer-only endpoints
                        .requestMatchers("/customer/**").hasRole("CUSTOMER")
                        
                        // User endpoints - authenticated only
                        .requestMatchers("/users/**").authenticated()
                        
                        // Default: require authentication for any other route
                        .anyRequest().authenticated()
                )
                .oauth2Login(Customizer.withDefaults())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .logout(logout -> logout
                        .logoutSuccessHandler(cognitoLogoutHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            CognitoGroupsAuthoritiesConverter cognitoGroupsAuthoritiesConverter
    ) {
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
        // Mantem o prefixo padrao SCOPE_ para claims scope/scp.
        scopesConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new DelegatingJwtGrantedAuthoritiesConverter(scopesConverter, cognitoGroupsAuthoritiesConverter)
        );
        return converter;
    }
}
