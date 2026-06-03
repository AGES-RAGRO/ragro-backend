package br.com.ragro.config;

import java.time.Duration;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * NVIDIA LLM API client config for the recommendation reranker (Spring AI).
 *
 * <p>NVIDIA's LLM API is OpenAI-compatible, so it is consumed through Spring AI's OpenAI client
 * ({@code spring.ai.openai.*}, base-url {@code https://integrate.api.nvidia.com}). Spring AI 1.0.3
 * exposes no HTTP timeout property for the OpenAI client and its default {@link RestClient} has no
 * read timeout — a hung request would block the worker thread indefinitely. This overrides the
 * {@link OpenAiApi} bean (declared {@code ConditionalOnMissingBean} by autoconfig) with explicit
 * connect/read timeouts; the autoconfigured {@code OpenAiChatModel} is then built on top of it.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "ragro.recommendations.rerank",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NvidiaChatConfig {

  @Bean
  public OpenAiApi openAiApi(
      @Value("${spring.ai.openai.base-url}") String baseUrl,
      @Value("${spring.ai.openai.api-key:}") String apiKey,
      @Value("${spring.ai.openai.chat.completions-path:/v1/chat/completions}") String completionsPath,
      @Value("${nvidia.connect-timeout-ms:2000}") long connectTimeoutMs,
      @Value("${nvidia.read-timeout-ms:60000}") long readTimeoutMs) {
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            .withReadTimeout(Duration.ofMillis(readTimeoutMs));
    RestClient.Builder restClientBuilder =
        RestClient.builder().requestFactory(ClientHttpRequestFactories.get(settings));
    return OpenAiApi.builder()
        .baseUrl(baseUrl)
        .apiKey(apiKey)
        .completionsPath(completionsPath)
        .restClientBuilder(restClientBuilder)
        .build();
  }
}
