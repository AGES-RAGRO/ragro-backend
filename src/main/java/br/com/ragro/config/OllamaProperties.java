package br.com.ragro.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

  private String baseUrl;
  private String model;
  private int connectTimeoutMs;
  private int readTimeoutMs;
}
