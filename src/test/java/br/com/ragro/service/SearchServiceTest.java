package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.SearchRequest;
import br.com.ragro.controller.response.SearchResultResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.User;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private ProducerRepository producerRepository;

  @InjectMocks private SearchService searchService;

  @Test
  void shouldReturnProductsAndProducers_whenSearchMatchesBoth() {
    SearchRequest request = new SearchRequest();
    request.setQuery("tomate");
    request.setCategory("Horta");

    Product product = buildProduct("Tomate Cereja", "Horta");
    Producer producer = buildProducer("Sítio Boa Colheita", "Mariana Alves");

    when(productRepository.searchActiveMarketplaceProducts("tomate", "Horta"))
        .thenReturn(List.of(product));
    when(producerRepository.searchMarketplace("tomate", "Horta")).thenReturn(List.of(producer));

    List<SearchResultResponse> response = searchService.search(request);

    assertThat(response).hasSize(2);
    assertThat(response.get(0).getType()).isEqualTo("product");
    assertThat(response.get(0).getName()).isEqualTo("Tomate Cereja");
    assertThat(response.get(0).getSubtitle()).isEqualTo("Sítio Boa Colheita");
    assertThat(response.get(0).getCategory()).isEqualTo("Horta");
    assertThat(response.get(1).getType()).isEqualTo("producer");
    assertThat(response.get(1).getName()).isEqualTo("Sítio Boa Colheita");
    assertThat(response.get(1).getSubtitle()).isEqualTo("Mariana Alves");
    assertThat(response.get(1).getReviewCount()).isEqualTo(24);
  }

  @Test
  void shouldSendNullCategoryToRepositories_whenCategoryIsBlank() {
    SearchRequest request = new SearchRequest();
    request.setQuery("alface");
    request.setCategory("   ");

    when(productRepository.searchActiveMarketplaceProducts("alface", null)).thenReturn(List.of());
    when(producerRepository.searchMarketplace("alface", null)).thenReturn(List.of());

    List<SearchResultResponse> response = searchService.search(request);

    assertThat(response).isEmpty();
    verify(productRepository).searchActiveMarketplaceProducts("alface", null);
    verify(producerRepository).searchMarketplace("alface", null);
  }

  private Product buildProduct(String name, String categoryName) {
    Producer producer = buildProducer("Sítio Boa Colheita", "Mariana Alves");

    ProductCategory category = new ProductCategory();
    category.setId(1);
    category.setName(categoryName);

    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setFarmer(producer);
    product.setName(name);
    product.setDescription("Produto fresco");
    product.setPrice(BigDecimal.valueOf(12.90));
    product.setUnityType("kg");
    product.setImageS3("products/tomate.jpg");
    product.getCategories().add(category);
    return product;
  }

  private Producer buildProducer(String farmName, String ownerName) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName(ownerName);
    user.setActive(true);

    Producer producer = new Producer();
    producer.setId(user.getId());
    producer.setUser(user);
    producer.setFarmName(farmName);
    producer.setAvatarS3("producers/avatar.jpg");
    producer.setAverageRating(BigDecimal.valueOf(4.8));
    producer.setTotalReviews(24);
    return producer;
  }
}
