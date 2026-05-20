package br.com.ragro.service.impl.ollama;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OllamaRankedOutput {

  private List<OllamaRankedEntry> ranked;

  @Getter
  @Setter
  @NoArgsConstructor
  public static class OllamaRankedEntry {

    private String productId;
    private Double score;
    private String reason;
  }
}
