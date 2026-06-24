package br.com.ragro.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ragro.controller.request.ProductPhotoRequest;
import br.com.ragro.controller.request.ProductRequest;
import br.com.ragro.controller.response.ProductResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.ProductPhoto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductMapperTest {

  private Producer farmer() {
    Producer p = new Producer();
    p.setId(UUID.randomUUID());
    return p;
  }

  private ProductRequest request() {
    ProductRequest r = new ProductRequest();
    r.setName("  Tomate Orgânico  ");
    r.setDescription("Fresco da horta");
    r.setPrice(new BigDecimal("9.90"));
    r.setUnityType("  KG  ");
    r.setStockQuantity(new BigDecimal("100.000"));
    r.setImageS3("img/key.jpg");
    r.setActive(true);
    return r;
  }

  private ProductCategory category(int id, String name) {
    ProductCategory c = new ProductCategory();
    c.setId(id);
    c.setName(name);
    c.setDescription(name + " desc");
    return c;
  }

  // ─── toEntity / applyRequest ──────────────────────────────────────────────

  @Test
  void toEntity_mapsFarmerAndNormalizedFields() {
    Producer farmer = farmer();

    Product product = ProductMapper.toEntity(farmer, request());

    assertThat(product).isNotNull();
    assertThat(product.getFarmer()).isSameAs(farmer);
    assertThat(product.getName()).isEqualTo("Tomate Orgânico"); // trimmed
    assertThat(product.getDescription()).isEqualTo("Fresco da horta");
    assertThat(product.getPrice()).isEqualByComparingTo("9.90");
    assertThat(product.getUnityType()).isEqualTo("kg"); // trimmed + lowercased
    assertThat(product.getStockQuantity()).isEqualByComparingTo("100.000");
    assertThat(product.getImageS3()).isEqualTo("img/key.jpg");
    assertThat(product.isActive()).isTrue();
  }

  @Test
  void applyRequest_setsActiveFalseWhenProvided() {
    Product product = new Product();
    ProductRequest r = request();
    r.setActive(false);

    ProductMapper.applyRequest(product, r);

    assertThat(product.isActive()).isFalse();
  }

  @Test
  void applyRequest_leavesActiveUntouchedWhenNull() {
    Product product = new Product();
    product.setActive(false); // pre-existing value must survive a null in the request
    ProductRequest r = request();
    r.setActive(null);

    ProductMapper.applyRequest(product, r);

    assertThat(product.isActive()).isFalse();
  }

  // ─── replaceCategories / replacePhotos ────────────────────────────────────

  @Test
  void replaceCategories_clearsExistingThenAddsNew() {
    Product product = new Product();
    product.getCategories().add(category(1, "Antiga"));

    ProductMapper.replaceCategories(product, List.of(category(2, "Nova")));

    assertThat(product.getCategories()).extracting(ProductCategory::getName).containsExactly("Nova");
  }

  @Test
  void replacePhotos_clearsExistingAndMapsRequests() {
    Product product = new Product();
    ProductPhoto old = new ProductPhoto();
    old.setUrl("old.jpg");
    product.getPhotos().add(old);

    ProductPhotoRequest req = new ProductPhotoRequest();
    req.setUrl("  new.jpg  ");
    req.setDisplayOrder((short) 2);

    ProductMapper.replacePhotos(product, List.of(req));

    assertThat(product.getPhotos()).hasSize(1);
    ProductPhoto photo = product.getPhotos().get(0);
    assertThat(photo.getUrl()).isEqualTo("new.jpg"); // trimmed
    assertThat(photo.getDisplayOrder()).isEqualTo((short) 2);
    assertThat(photo.getProduct()).isSameAs(product);
  }

  @Test
  void replacePhotos_nullRequestsJustClears() {
    Product product = new Product();
    product.getPhotos().add(new ProductPhoto());

    ProductMapper.replacePhotos(product, null);

    assertThat(product.getPhotos()).isEmpty();
  }

  // ─── toResponse ───────────────────────────────────────────────────────────

  @Test
  void toResponse_mapsAllFieldsWithSortedCategoriesAndPhotos() {
    Producer farmer = farmer();
    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setFarmer(farmer);
    product.setName("Tomate");
    product.setDescription("desc");
    product.setPrice(new BigDecimal("9.90"));
    product.setUnityType("kg");
    product.setStockQuantity(new BigDecimal("50.000"));
    product.setImageS3("img.jpg");
    product.setActive(true);
    product.setCreatedAt(OffsetDateTime.now().minusDays(1));
    product.setUpdatedAt(OffsetDateTime.now());
    product.getCategories().add(category(1, "Zucchini"));
    product.getCategories().add(category(2, "Abóbora"));

    ProductPhoto p1 = new ProductPhoto();
    p1.setId(UUID.randomUUID());
    p1.setUrl("second.jpg");
    p1.setDisplayOrder((short) 2);
    p1.setCreatedAt(OffsetDateTime.now());
    ProductPhoto p0 = new ProductPhoto();
    p0.setId(UUID.randomUUID());
    p0.setUrl("first.jpg");
    p0.setDisplayOrder((short) 1);
    p0.setCreatedAt(OffsetDateTime.now());
    product.getPhotos().add(p1);
    product.getPhotos().add(p0);

    ProductResponse response = ProductMapper.toResponse(product);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(product.getId());
    assertThat(response.getFarmerId()).isEqualTo(farmer.getId());
    assertThat(response.getName()).isEqualTo("Tomate");
    assertThat(response.getDescription()).isEqualTo("desc");
    assertThat(response.getPrice()).isEqualByComparingTo("9.90");
    assertThat(response.getUnityType()).isEqualTo("kg");
    assertThat(response.getStockQuantity()).isEqualByComparingTo("50.000");
    assertThat(response.getImageS3()).isEqualTo("img.jpg");
    assertThat(response.isActive()).isTrue();
    assertThat(response.getCreatedAt()).isEqualTo(product.getCreatedAt());
    assertThat(response.getUpdatedAt()).isEqualTo(product.getUpdatedAt());
    // categories sorted by name (Abóbora before Zucchini)
    assertThat(response.getCategories()).extracting("name").containsExactly("Abóbora", "Zucchini");
    assertThat(response.getCategories().get(0).getId()).isEqualTo(2);
    assertThat(response.getCategories().get(0).getDescription()).isEqualTo("Abóbora desc");
    // photos sorted by displayOrder asc
    assertThat(response.getPhotos()).extracting("url").containsExactly("first.jpg", "second.jpg");
    assertThat(response.getPhotos().get(0).getId()).isEqualTo(p0.getId());
    assertThat(response.getPhotos().get(0).getDisplayOrder()).isEqualTo((short) 1);
  }
}
