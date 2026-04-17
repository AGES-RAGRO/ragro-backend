package br.com.ragro.mapper;

import br.com.ragro.controller.request.ProductPhotoRequest;
import br.com.ragro.controller.request.ProductRequest;
import br.com.ragro.controller.response.ProductCategoryResponse;
import br.com.ragro.controller.response.ProductPhotoResponse;
import br.com.ragro.controller.response.ProductResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.ProductPhoto;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProductMapper {

  @NonNull
  public static Product toEntity(@NonNull Producer farmer, @NonNull ProductRequest request) {
    Product product = new Product();
    product.setFarmer(farmer);
    applyRequest(product, request);
    return product;
  }

  public static void applyRequest(@NonNull Product product, @NonNull ProductRequest request) {
    product.setName(request.getName().trim());
    product.setDescription(request.getDescription());
    product.setPrice(request.getPrice());
    product.setUnityType(request.getUnityType().trim().toLowerCase());
    product.setStockQuantity(request.getStockQuantity());
    product.setImageS3(request.getImageS3());
    if (request.getActive() != null) {
      product.setActive(request.getActive());
    }
  }

  public static void replaceCategories(
      @NonNull Product product, @NonNull Collection<ProductCategory> categories) {
    product.getCategories().clear();
    product.getCategories().addAll(categories);
  }

  public static void replacePhotos(
      @NonNull Product product, List<ProductPhotoRequest> photoRequests) {
    product.getPhotos().clear();
    if (photoRequests == null) {
      return;
    }
    photoRequests.forEach(
        request -> {
          ProductPhoto photo = new ProductPhoto();
          photo.setProduct(product);
          photo.setUrl(request.getUrl().trim());
          photo.setDisplayOrder(request.getDisplayOrder());
          product.getPhotos().add(photo);
        });
  }

  @NonNull
  public static ProductResponse toResponse(@NonNull Product product) {
    return ProductResponse.builder()
        .id(product.getId())
        .farmerId(product.getFarmer().getId())
        .name(product.getName())
        .description(product.getDescription())
        .price(product.getPrice())
        .unityType(product.getUnityType())
        .stockQuantity(product.getStockQuantity())
        .imageS3(product.getImageS3())
        .active(product.isActive())
        .createdAt(product.getCreatedAt())
        .updatedAt(product.getUpdatedAt())
        .categories(toCategoryResponses(product))
        .photos(toPhotoResponses(product))
        .build();
  }

  private static List<ProductCategoryResponse> toCategoryResponses(Product product) {
    return product.getCategories().stream()
        .sorted(Comparator.comparing(ProductCategory::getName))
        .map(ProductMapper::toCategoryResponse)
        .toList();
  }

  private static ProductCategoryResponse toCategoryResponse(ProductCategory category) {
    return ProductCategoryResponse.builder()
        .id(category.getId())
        .name(category.getName())
        .description(category.getDescription())
        .build();
  }

  private static List<ProductPhotoResponse> toPhotoResponses(Product product) {
    return product.getPhotos().stream()
        .sorted(
            Comparator.comparing(ProductPhoto::getDisplayOrder)
                .thenComparing(
                    ProductPhoto::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(ProductMapper::toPhotoResponse)
        .toList();
  }

  private static ProductPhotoResponse toPhotoResponse(ProductPhoto photo) {
    return ProductPhotoResponse.builder()
        .id(photo.getId())
        .url(photo.getUrl())
        .displayOrder(photo.getDisplayOrder())
        .createdAt(photo.getCreatedAt())
        .build();
  }
}
