package br.com.ragro.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * {@link RestClient} dedicado da Google Routes API, com timeouts explícitos.
 *
 * <p>O {@code RestClient.Builder} autoconfigurado do Boot não tem read-timeout: uma chamada que
 * trava no meio da leitura bloquearia o worker (e a conexão do pool) indefinidamente — e, pior,
 * impediria o retry de transporte de {@link br.com.ragro.service.GoogleRoutesService} de disparar.
 * Por isso timeouts são pré-requisito do retry, não opcional.
 *
 * <p>Read 10s porque {@code computeRoutes} com {@code TRAFFIC_AWARE} + otimização de ordem para até
 * 25 paradas leva legitimamente alguns segundos; um read mais curto abortaria chamadas tarifadas que
 * o Google estava prestes a concluir. Connect 2s é folgado para o endpoint anycast do Google.
 */
@Configuration
public class GoogleRoutesClientConfig {

  @Bean
  public RestClient googleRoutesRestClient(
      RestClient.Builder builder,
      @Value("${google.maps.api-key}") String apiKey,
      @Value("${google.routes.base-url:https://routes.googleapis.com}") String baseUrl,
      @Value("${google.routes.connect-timeout-ms:2000}") long connectTimeoutMs,
      @Value("${google.routes.read-timeout-ms:10000}") long readTimeoutMs) {
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            .withReadTimeout(Duration.ofMillis(readTimeoutMs));
    return builder
        .baseUrl(baseUrl)
        .defaultHeader("X-Goog-Api-Key", apiKey)
        .defaultHeader("Content-Type", "application/json")
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        .build();
  }
}
