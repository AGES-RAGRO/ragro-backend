package br.com.ragro.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Dedicated {@link RestClient} for the Google Routes API with explicit timeouts.
 *
 * <p>Boot's autoconfigured builder has no read-timeout; without one a stalled read blocks the worker
 * indefinitely and the transport retry in {@link br.com.ragro.service.GoogleRoutesService} never fires.
 *
 * <p>Read 10s: {@code computeRoutes} with {@code TRAFFIC_AWARE} + optimization for up to 25 stops takes
 * a few seconds (shorter would abort billed calls). Connect 2s for Google's anycast endpoint.
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
