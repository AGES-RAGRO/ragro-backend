package br.com.ragro.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OllamaConfigTest {

  @Test
  void shouldCreateOllamaRestClientBean() {
    OllamaConfig config = new OllamaConfig(buildProperties());

    RestClient client = config.ollamaRestClient();

    assertThat(client).isNotNull();
  }

  @Test
  void shouldExposeIndependentConnectAndReadTimeouts() {
    OllamaProperties properties = buildProperties();

    assertThat(properties.getConnectTimeoutMs()).isEqualTo(2000);
    assertThat(properties.getReadTimeoutMs()).isEqualTo(30000);
    assertThat(properties.getConnectTimeoutMs()).isNotEqualTo(properties.getReadTimeoutMs());
  }

  @Test
  void shouldExposeBaseUrlAndModel() {
    OllamaProperties properties = buildProperties();

    assertThat(properties.getBaseUrl()).isEqualTo("http://test-ollama:11434");
    assertThat(properties.getModel()).isEqualTo("gemma2:2b");
  }

  private OllamaProperties buildProperties() {
    OllamaProperties properties = new OllamaProperties();
    properties.setBaseUrl("http://test-ollama:11434");
    properties.setModel("gemma2:2b");
    properties.setConnectTimeoutMs(2000);
    properties.setReadTimeoutMs(30000);
    return properties;
  }
}
