package br.com.ragro.service.api;

import br.com.ragro.domain.llm.Candidate;
import br.com.ragro.domain.llm.CustomerFeatures;
import br.com.ragro.domain.llm.RankedItem;
import java.util.List;

public interface LlmRerankerPort {

  List<RankedItem> rerank(List<Candidate> candidates, CustomerFeatures features);
}
