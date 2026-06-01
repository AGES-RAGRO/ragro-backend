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
 * Ollama HTTP client config for the recommendation reranker (Spring AI).
 *
 * <p>Spring AI 1.0.3 only exposes {@code spring.ai.ollama.base-url}, with no timeout property. The
 * client's ~10s default is shorter than CPU LLM inference, so every call timed out and
 * {@code RecommendationService} fell back to the heuristic. This overrides the {@link OllamaApi}
 * bean (declared {@code ConditionalOnMissingBean} by autoconfig) with explicit connect/read
 * timeouts. Only affects the Ollama client.
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
