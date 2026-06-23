package br.com.ragro.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Per-minute limits for {@link RateLimitFilter}. Public auth endpoints are limited by IP (no user
 * yet); authenticated endpoints with external cost (LLM, Google Maps) are limited per user.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ragro.rate-limit")
public class RateLimitProperties {

  /** Turns off the whole filter (e.g. test profile). */
  private boolean enabled = true;

  /** POST /auth/register/** — per IP. Mitigates mass signup and enumeration. */
  @Min(1)
  private int registerPerMinute = 5;

  /** POST /auth/password/forgot — per IP. Mitigates email bombing via Keycloak. */
  @Min(1)
  private int forgotPerMinute = 5;

  /** GET /recommendations — per user. Protects against the cost of LLM calls. */
  @Min(1)
  private int recommendationsPerMinute = 10;

  /** /routes/** — per user. Protects the Google API quota/cost. */
  @Min(1)
  private int routesPerMinute = 10;
}
