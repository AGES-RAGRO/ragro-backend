package br.com.ragro.mapper;

import br.com.ragro.controller.response.SearchResultResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import java.util.Comparator;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SearchMapper {

  @NonNull
  public static SearchResultResponse toProductResponse(@NonNull Product product) {
    return SearchResultResponse.builder()
        .id(product.getId())
        .type("product")
        .name(product.getName())
        .subtitle(product.getFarmer().getFarmName())
        .imageUrl(product.getImageS3())
        .producerId(product.getFarmer().getId())
        .farmerId(product.getFarmer().getId())
        .price(product.getPrice())
        .category(primaryCategoryName(product))
        .unit(product.getUnityType())
        .build();
  }

  @NonNull
  public static SearchResultResponse toProducerResponse(@NonNull Producer producer) {
    return SearchResultResponse.builder()
        .id(producer.getId())
        .type("producer")
        .name(producer.getFarmName())
        .subtitle(producer.getUser().getName())
        .imageUrl(producer.getAvatarS3())
        .producerId(producer.getId())
        .farmerId(producer.getId())
        .rating(producer.getAverageRating())
        .reviewCount(producer.getTotalReviews())
        .build();
  }

  private static String primaryCategoryName(Product product) {
    return product.getCategories().stream()
        .map(ProductCategory::getName)
        .sorted(Comparator.naturalOrder())
        .findFirst()
        .orElse(null);
  }
}
