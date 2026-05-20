package br.com.ragro.service.impl.ollama;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OllamaResponseMessage {

  private String role;
  private String content;
}
