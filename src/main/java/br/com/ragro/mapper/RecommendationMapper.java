package br.com.ragro.mapper;

import br.com.ragro.controller.response.RecommendationProductResponse;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.enums.RecommendationReason;
import br.com.ragro.service.MinioStorageService;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RecommendationMapper {

  /**
   * Maps a recommended product to its response. Composes the public image URL via {@link
   * MinioStorageService} when provided; accepts {@code null} (raw key) for legacy calls or tests.
   */
  public RecommendationProductResponse toResponse(
      Product product, int score, RecommendationReason reason, MinioStorageService storage) {
    String imageUrl =
        storage != null ? storage.composePublicUrl(product.getImageS3()) : product.getImageS3();
    return RecommendationProductResponse.builder()
        .id(product.getId())
        .name(product.getName())
        .price(product.getPrice())
        .unityType(product.getUnityType())
        .imageS3(imageUrl)
        .farmerId(product.getFarmer() != null ? product.getFarmer().getId() : null)
        .farmName(product.getFarmer() != null ? product.getFarmer().getFarmName() : null)
        .categoryNames(product.getCategories().stream().map(ProductCategory::getName).toList())
        .score(score)
        .reason(reason)
        .build();
  }
}
