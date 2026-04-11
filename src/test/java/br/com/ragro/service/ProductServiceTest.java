package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.ProductPhotoRequest;
import br.com.ragro.controller.request.ProductRequest;
import br.com.ragro.controller.response.ProductResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.ProductCategoryRepository;
import br.com.ragro.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private UserService userService;

  @Mock private ProducerRepository producerRepository;

  @Mock private ProductRepository productRepository;

  @Mock private ProductCategoryRepository productCategoryRepository;

  @InjectMocks private ProductService productService;

  @Test
  void getMyProducts_shouldReturnProductsFromAuthenticatedFarmer() {
    Producer farmer = buildAuthenticatedFarmer();
    Product product = buildProduct(farmer);
    when(productRepository.findAllByFarmerId(farmer.getId())).thenReturn(List.of(product));

    List<ProductResponse> response = productService.getMyProducts(jwt());

    assertThat(response).hasSize(1);
    assertThat(response.getFirst().getId()).isEqualTo(product.getId());
    assertThat(response.getFirst().getFarmerId()).isEqualTo(farmer.getId());
  }

  @Test
  void getMyProductById_shouldThrowNotFound_whenProductDoesNotBelongToFarmer() {
    Producer farmer = buildAuthenticatedFarmer();
    UUID productId = UUID.randomUUID();
    when(productRepository.findByIdAndFarmerId(productId, farmer.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.getMyProductById(productId, jwt()))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produto não encontrado");
  }

  @Test
  void createProduct_shouldCreateProductWithCategoriesAndPhotos() {
    Producer farmer = buildAuthenticatedFarmer();
    ProductCategory category = buildCategory(1, "Fruits");
    when(productCategoryRepository.findAllById(List.of(1))).thenReturn(List.of(category));
    when(productRepository.saveAndFlush(any(Product.class)))
        .thenAnswer(
            invocation -> {
              Product product = invocation.getArgument(0);
              product.setId(UUID.randomUUID());
              return product;
            });

    ProductResponse response = productService.createProduct(productRequest(), jwt());

    assertThat(response.getName()).isEqualTo("Organic strawberries");
    assertThat(response.getUnityType()).isEqualTo("kg");
    assertThat(response.getCategories()).hasSize(1);
    assertThat(response.getPhotos()).hasSize(1);
    assertThat(response.isActive()).isTrue();
  }

  @Test
  void createProduct_shouldThrowBusinessException_whenUnityTypeIsInvalid() {
    buildAuthenticatedFarmer();
    ProductRequest request = productRequest();
    request.setUnityType("crate");

    assertThatThrownBy(() -> productService.createProduct(request, jwt()))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Unity type inválido");
  }

  @Test
  void createProduct_shouldThrowNotFound_whenCategoryDoesNotExist() {
    buildAuthenticatedFarmer();
    when(productCategoryRepository.findAllById(List.of(1))).thenReturn(List.of());

    assertThatThrownBy(() -> productService.createProduct(productRequest(), jwt()))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Categoria de produto não encontrada");
  }

  @Test
  void updateProduct_shouldUpdateOwnedProduct() {
    Producer farmer = buildAuthenticatedFarmer();
    Product product = buildProduct(farmer);
    when(productRepository.findByIdAndFarmerId(product.getId(), farmer.getId()))
        .thenReturn(Optional.of(product));
    when(productCategoryRepository.findAllById(List.of(1)))
        .thenReturn(List.of(buildCategory(1, "Fruits")));
    when(productRepository.saveAndFlush(product)).thenReturn(product);

    ProductRequest request = productRequest();
    request.setName("Updated strawberries");

    ProductResponse response = productService.updateProduct(product.getId(), request, jwt());

    assertThat(response.getName()).isEqualTo("Updated strawberries");
    assertThat(response.getCategories()).hasSize(1);
    verify(productRepository).saveAndFlush(product);
  }

  @Test
  void deactivateProduct_shouldSetActiveFalse() {
    Producer farmer = buildAuthenticatedFarmer();
    Product product = buildProduct(farmer);
    when(productRepository.findByIdAndFarmerId(product.getId(), farmer.getId()))
        .thenReturn(Optional.of(product));
    when(productRepository.saveAndFlush(product)).thenReturn(product);

    ProductResponse response = productService.deactivateProduct(product.getId(), jwt());

    assertThat(response.isActive()).isFalse();
    verify(productRepository).saveAndFlush(product);
  }

  private Producer buildAuthenticatedFarmer() {
    UUID farmerId = UUID.randomUUID();
    User user = new User();
    user.setId(farmerId);
    user.setType(TypeUser.FARMER);
    user.setActive(true);

    Producer farmer = new Producer();
    farmer.setId(farmerId);
    farmer.setUser(user);

    when(userService.getAuthenticatedUser(any(Jwt.class))).thenReturn(user);
    when(producerRepository.findById(farmerId)).thenReturn(Optional.of(farmer));
    return farmer;
  }

  private Product buildProduct(Producer farmer) {
    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setFarmer(farmer);
    product.setName("Organic strawberries");
    product.setPrice(new BigDecimal("18.90"));
    product.setUnityType("kg");
    product.setStockQuantity(new BigDecimal("35.500"));
    product.setActive(true);
    return product;
  }

  private ProductCategory buildCategory(Integer id, String name) {
    ProductCategory category = new ProductCategory();
    category.setId(id);
    category.setName(name);
    return category;
  }

  private ProductRequest productRequest() {
    ProductPhotoRequest photo = new ProductPhotoRequest();
    photo.setUrl("s3://bucket/products/strawberries-1.jpg");
    photo.setDisplayOrder((short) 0);

    ProductRequest request = new ProductRequest();
    request.setName("Organic strawberries");
    request.setDescription("Freshly harvested strawberries.");
    request.setPrice(new BigDecimal("18.90"));
    request.setUnityType("KG");
    request.setStockQuantity(new BigDecimal("35.500"));
    request.setImageS3("s3://bucket/products/strawberries.jpg");
    request.setActive(true);
    request.setCategoryIds(List.of(1));
    request.setPhotos(List.of(photo));
    return request;
  }

  private Jwt jwt() {
    return new Jwt(
        "token",
        Instant.now(),
        Instant.now().plusSeconds(300),
        Map.of("alg", "none"),
        Map.of("sub", "farmer-sub", "email", "farmer@example.com"));
  }
}
