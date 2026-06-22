package br.com.ragro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra o {@link RateLimitFilter} fora da security chain (ver javadoc do filtro). */
@Configuration
public class RateLimitConfig {

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
      RateLimitProperties properties, ObjectMapper objectMapper) {
    FilterRegistrationBean<RateLimitFilter> registration =
        new FilterRegistrationBean<>(new RateLimitFilter(properties, objectMapper));
    // springSecurityFilterChain roda em order -100; 0 garante execução DEPOIS da security chain,
    // com SecurityContext populado (chave por usuário) e sem consumir orçamento de requests 401.
    registration.setOrder(0);
    registration.addUrlPatterns("/auth/*", "/recommendations", "/routes", "/routes/*");
    return registration;
  }
}
