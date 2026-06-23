package br.com.ragro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the {@link RateLimitFilter} outside the security chain (see the filter's javadoc). */
@Configuration
public class RateLimitConfig {

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
      RateLimitProperties properties, ObjectMapper objectMapper) {
    FilterRegistrationBean<RateLimitFilter> registration =
        new FilterRegistrationBean<>(new RateLimitFilter(properties, objectMapper));
    // Order 0 runs after the security chain (order -100): SecurityContext is populated for the
    // per-user key, and 401 requests don't consume budget.
    registration.setOrder(0);
    registration.addUrlPatterns("/auth/*", "/recommendations", "/routes", "/routes/*");
    return registration;
  }
}
