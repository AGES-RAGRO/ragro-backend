package br.com.ragro.service.impl.ollama;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OllamaChatResponse {

  private OllamaResponseMessage message;
  private boolean done;
}
