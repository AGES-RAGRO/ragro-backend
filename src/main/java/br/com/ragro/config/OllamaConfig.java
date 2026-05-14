package br.com.ragro.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class OllamaConfig {

  private final OllamaProperties properties;

  @Bean
  public RestClient ollamaRestClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) Duration.ofMillis(properties.getTimeoutMs()).toMillis());
    factory.setReadTimeout((int) Duration.ofMillis(properties.getTimeoutMs()).toMillis());
    return RestClient.builder()
        .baseUrl(properties.getBaseUrl())
        .requestFactory(factory)
        .build();
  }
}
