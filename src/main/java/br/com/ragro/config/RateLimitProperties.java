package br.com.ragro.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Limites por minuto do {@link RateLimitFilter}. Endpoints públicos de auth são limitados por IP
 * (não há usuário ainda); endpoints autenticados de custo externo (LLM, Google Maps) por usuário.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ragro.rate-limit")
public class RateLimitProperties {

  /** Desliga o filtro inteiro (ex.: profile de teste). */
  private boolean enabled = true;

  /** POST /auth/register/** — por IP. Mitiga cadastro em massa e enumeração. */
  @Min(1)
  private int registerPerMinute = 5;

  /** POST /auth/password/forgot — por IP. Mitiga e-mail bombing via Keycloak. */
  @Min(1)
  private int forgotPerMinute = 5;

  /** GET /recommendations — por usuário. Protege o custo de chamadas de LLM. */
  @Min(1)
  private int recommendationsPerMinute = 10;

  /** /routes/** — por usuário. Protege a quota/custo da API do Google. */
  @Min(1)
  private int routesPerMinute = 10;
}
