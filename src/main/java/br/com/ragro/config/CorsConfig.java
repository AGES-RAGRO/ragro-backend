package br.com.ragro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Only allow requests from specific trusted origins
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",      // Local frontend
                "http://localhost:8080",      // Local backend (for testing)
                "https://yourdomain.com"      // Replace with your production domain
        ));
        
        // Only allow necessary HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Only allow necessary headers
        configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization"));
        
        // Do NOT expose all headers
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        
        // Allow credentials (cookies, auth headers)
        configuration.setAllowCredentials(true);
        
        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
