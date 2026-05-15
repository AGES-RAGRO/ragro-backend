package br.com.ragro.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(classes = {OllamaConfig.class, OllamaProperties.class})
@EnableConfigurationProperties(OllamaProperties.class)
@TestPropertySource(
    properties = {
      "ollama.base-url=http://test-ollama:11434",
      "ollama.model=gemma2:2b",
      "ollama.connect-timeout-ms=2000",
      "ollama.read-timeout-ms=30000"
    })
class OllamaConfigTest {

  @Autowired private RestClient ollamaRestClient;

  @Autowired private OllamaProperties ollamaProperties;

  @Test
  void shouldCreateOllamaRestClientBean() {
    assertThat(ollamaRestClient).isNotNull();
  }

  @Test
  void shouldApplyConnectAndReadTimeoutsIndependently() {
    assertThat(ollamaProperties.getConnectTimeoutMs()).isEqualTo(2000);
    assertThat(ollamaProperties.getReadTimeoutMs()).isEqualTo(30000);
  }

  @Test
  void shouldBindPropertiesCorrectlyFromYaml() {
    assertThat(ollamaProperties.getBaseUrl()).isEqualTo("http://test-ollama:11434");
    assertThat(ollamaProperties.getModel()).isEqualTo("gemma2:2b");
    assertThat(ollamaProperties.getConnectTimeoutMs()).isEqualTo(2000);
    assertThat(ollamaProperties.getReadTimeoutMs()).isEqualTo(30000);
  }
}
