package br.com.ragro.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ragro.controller.response.RecommendationProductResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.enums.RecommendationReason;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationMapperTest {

  @Test
  void toResponse_shouldMapAllFields() {
    UUID productId = UUID.randomUUID();
    UUID farmerId = UUID.randomUUID();

    Producer farmer = new Producer();
    farmer.setId(farmerId);
    farmer.setFarmName("Fazenda Sol Nascente");

    ProductCategory category = new ProductCategory();
    category.setName("frutas");

    Product product = new Product();
    product.setId(productId);
    product.setName("Morango Orgânico");
    product.setPrice(new BigDecimal("18.90"));
    product.setUnityType("kg");
    product.setImageS3("s3://bucket/morango.jpg");
    product.setFarmer(farmer);
    product.getCategories().add(category);

    RecommendationProductResponse response =
        RecommendationMapper.toResponse(product, 95, RecommendationReason.PURCHASE_HISTORY);

    assertThat(response.getId()).isEqualTo(productId);
    assertThat(response.getName()).isEqualTo("Morango Orgânico");
    assertThat(response.getPrice()).isEqualByComparingTo("18.90");
    assertThat(response.getUnityType()).isEqualTo("kg");
    assertThat(response.getImageS3()).isEqualTo("s3://bucket/morango.jpg");
    assertThat(response.getFarmerId()).isEqualTo(farmerId);
    assertThat(response.getFarmName()).isEqualTo("Fazenda Sol Nascente");
    assertThat(response.getCategoryNames()).containsExactly("frutas");
    assertThat(response.getScore()).isEqualTo(95);
    assertThat(response.getReason()).isEqualTo(RecommendationReason.PURCHASE_HISTORY);
  }

  @Test
  void toResponse_shouldReturnEmptyCategoryNames_whenProductHasNoCategories() {
    Producer farmer = new Producer();
    farmer.setId(UUID.randomUUID());
    farmer.setFarmName("Fazenda Teste");

    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setName("Produto Sem Categoria");
    product.setPrice(BigDecimal.TEN);
    product.setUnityType("unit");
    product.setFarmer(farmer);

    RecommendationProductResponse response =
        RecommendationMapper.toResponse(product, 10, RecommendationReason.TRENDING);

    assertThat(response.getCategoryNames()).isEmpty();
  }
}
