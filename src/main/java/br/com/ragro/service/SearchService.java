package br.com.ragro.service;

import br.com.ragro.controller.request.SearchRequest;
import br.com.ragro.controller.response.SearchResultResponse;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.mapper.SearchMapper;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchService {

  private final ProductRepository productRepository;
  private final ProducerRepository producerRepository;

  @Transactional(readOnly = true)
  public List<SearchResultResponse> search(SearchRequest request) {
    if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
      throw new BusinessException("query is required");
    }

    String query = request.getQuery().trim();
    String category =
        request.getCategory() == null || request.getCategory().isBlank()
            ? null
            : request.getCategory().trim();

    List<SearchResultResponse> results = new ArrayList<>();

    productRepository.searchActiveMarketplaceProducts(query, category).stream()
        .map(SearchMapper::toProductResponse)
        .forEach(results::add);

    producerRepository.searchMarketplace(query, category).stream()
        .map(SearchMapper::toProducerResponse)
        .forEach(results::add);

    return results;
  }
}
