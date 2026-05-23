package br.com.ragro.mapper;

import br.com.ragro.controller.response.RecommendationProductResponse;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.enums.RecommendationReason;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RecommendationMapper {

  public RecommendationProductResponse toResponse(
      Product product, int score, RecommendationReason reason) {
    return RecommendationProductResponse.builder()
        .id(product.getId())
        .name(product.getName())
        .price(product.getPrice())
        .unityType(product.getUnityType())
        .imageS3(product.getImageS3())
        .farmerId(product.getFarmer() != null ? product.getFarmer().getId() : null)
        .farmName(product.getFarmer() != null ? product.getFarmer().getFarmName() : null)
        .categoryNames(product.getCategories().stream()
            .map(ProductCategory::getName)
            .toList())
        .score(score)
        .reason(reason)
        .build();
  }
}
