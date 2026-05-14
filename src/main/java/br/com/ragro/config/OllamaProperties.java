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

  private String baseUrl = "http://ollama:11434";
  private String model = "gemma2:2b";
  private int timeoutMs = 3000;
}
