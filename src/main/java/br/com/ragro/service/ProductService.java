package br.com.ragro.service;

import br.com.ragro.controller.request.ProductRequest;
import br.com.ragro.controller.response.ProductCategoryResponse;
import br.com.ragro.controller.response.ProductResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.mapper.ProductMapper;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.ProductCategoryRepository;
import br.com.ragro.repository.ProductRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

  private static final Set<String> ALLOWED_UNITY_TYPES =
      Set.of("kg", "g", "unit", "box", "liter", "ml", "dozen");

  private final UserService userService;
  private final ProducerRepository producerRepository;
  private final ProductRepository productRepository;
  private final ProductCategoryRepository productCategoryRepository;

  @Transactional(readOnly = true)
  public List<ProductResponse> getMyProducts(Jwt jwt) {
    Producer farmer = getAuthenticatedFarmer(jwt);
    return productRepository.findAllByFarmerId(farmer.getId()).stream()
        .map(ProductMapper::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductResponse getMyProductById(UUID id, Jwt jwt) {
    Producer farmer = getAuthenticatedFarmer(jwt);
    Product product = getProductOwnedByFarmer(id, farmer.getId());
    return ProductMapper.toResponse(product);
  }

  @Transactional
  public ProductResponse createProduct(ProductRequest request, Jwt jwt) {
    Producer farmer = getAuthenticatedFarmer(jwt);
    validateUnityType(request.getUnityType());
    Product product = ProductMapper.toEntity(farmer, request);
    ProductMapper.replaceCategories(product, getCategories(request.getCategoryIds()));
    ProductMapper.replacePhotos(product, request.getPhotos());
    return ProductMapper.toResponse(productRepository.saveAndFlush(product));
  }

  @Transactional
  public ProductResponse updateProduct(UUID id, ProductRequest request, Jwt jwt) {
    Producer farmer = getAuthenticatedFarmer(jwt);
    Product product = getProductOwnedByFarmer(id, farmer.getId());
    validateUnityType(request.getUnityType());
    ProductMapper.applyRequest(product, request);
    ProductMapper.replaceCategories(product, getCategories(request.getCategoryIds()));
    ProductMapper.replacePhotos(product, request.getPhotos());
    return ProductMapper.toResponse(productRepository.saveAndFlush(product));
  }

  @Transactional
  public ProductResponse deactivateProduct(UUID id, Jwt jwt) {
    Producer farmer = getAuthenticatedFarmer(jwt);
    Product product = getProductOwnedByFarmer(id, farmer.getId());
    product.setActive(false);
    return ProductMapper.toResponse(productRepository.saveAndFlush(product));
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> getActiveProductsByProducerId(UUID producerId) {
    if (!producerRepository.existsById(producerId)) {
      throw new NotFoundException("Produtor não encontrado");
    }
    return productRepository.findAllByFarmerIdAndActiveTrue(producerId).stream()
        .map(ProductMapper::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ProductCategoryResponse> getCategories() {
    return productCategoryRepository.findAll().stream()
        .map(
            category ->
                ProductCategoryResponse.builder()
                    .id(category.getId())
                    .name(category.getName())
                    .description(category.getDescription())
                    .build())
        .toList();
  }

  private Producer getAuthenticatedFarmer(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);
    if (user.getType() != TypeUser.FARMER) {
      throw new UnauthorizedException("Access restricted to farmers");
    }
    return producerRepository
        .findById(user.getId())
        .orElseThrow(() -> new NotFoundException("Dados do produtor não encontrados"));
  }

  private Product getProductOwnedByFarmer(UUID productId, UUID farmerId) {
    return productRepository
        .findByIdAndFarmerId(productId, farmerId)
        .orElseThrow(() -> new NotFoundException("Produto não encontrado"));
  }

  private List<ProductCategory> getCategories(List<Integer> categoryIds) {
    if (categoryIds == null || categoryIds.isEmpty()) {
      return List.of();
    }
    List<Integer> distinctIds = categoryIds.stream().distinct().toList();
    List<ProductCategory> categories = productCategoryRepository.findAllById(distinctIds);
    if (categories.size() != distinctIds.size()) {
      throw new NotFoundException("Categoria de produto não encontrada");
    }
    return categories;
  }

  private void validateUnityType(String unityType) {
    if (!ALLOWED_UNITY_TYPES.contains(unityType.trim().toLowerCase())) {
      throw new BusinessException("Unity type inválido");
    }
  }
}
