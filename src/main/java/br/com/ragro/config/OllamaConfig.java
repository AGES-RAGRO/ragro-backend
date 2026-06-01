package br.com.ragro.config;

import java.time.Duration;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuração do cliente HTTP do Ollama (reranker de recomendações via Spring AI).
 *
 * <p>O Spring AI 1.0.3 só expõe {@code spring.ai.ollama.base-url} por propriedade — NÃO há
 * configuração de timeout. O default do cliente HTTP (~10s) é menor que a inferência da LLM em CPU
 * (gemma2:2b leva ~10s+ só no aquecimento; com o prompt real de até 50 candidatos, mais), então
 * toda chamada estourava por timeout e o {@code RecommendationService} caía no fallback heurístico.
 *
 * <p>Esta classe sobrepõe o bean {@link OllamaApi} (o autoconfig o declara como {@code
 * ConditionalOnMissingBean}) com um {@link RestClient.Builder} que carrega connect/read-timeout
 * explícitos. Afeta SOMENTE o cliente do Ollama — o {@code KeycloakIdentityProviderService} usa um
 * {@code RestClient} próprio e não é tocado.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "ragro.recommendations.rerank",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OllamaConfig {

  @Bean
  public OllamaApi ollamaApi(
      @Value("${spring.ai.ollama.base-url}") String baseUrl,
      @Value("${ollama.connect-timeout-ms:2000}") long connectTimeoutMs,
      @Value("${ollama.read-timeout-ms:60000}") long readTimeoutMs) {
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            .withReadTimeout(Duration.ofMillis(readTimeoutMs));
    RestClient.Builder restClientBuilder =
        RestClient.builder().requestFactory(ClientHttpRequestFactories.get(settings));
    return OllamaApi.builder().baseUrl(baseUrl).restClientBuilder(restClientBuilder).build();
  }
}
