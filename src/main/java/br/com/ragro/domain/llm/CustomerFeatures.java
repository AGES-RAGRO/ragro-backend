package br.com.ragro.domain.llm;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** De-identified customer profile sent to the LLM during rerank. */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class CustomerFeatures {

  private List<String> preferredCategories = new ArrayList<>();
  private List<String> favoriteProducers = new ArrayList<>();

  public boolean isEmpty() {
    return preferredCategories.isEmpty() && favoriteProducers.isEmpty();
  }
}
