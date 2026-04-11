package br.com.ragro;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import static org.mockito.Mockito.mock;

@TestConfiguration
@EnableMethodSecurity
public class TestSecurityConfiguration {

  @Bean
  @Primary
  public JwtDecoder jwtDecoder() {
    return mock(JwtDecoder.class);
  }
}
