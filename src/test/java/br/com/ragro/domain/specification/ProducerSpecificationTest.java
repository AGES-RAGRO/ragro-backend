package br.com.ragro.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ragro.controller.request.ProducerFilter;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductCategory;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.ProductCategoryRepository;
import br.com.ragro.repository.ProductRepository;
import br.com.ragro.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Real-Postgres ({@code @DataJpaTest}, auto-rollback) test of {@link ProducerSpecification}. The
 * specification builds JPA Criteria predicates whose behavior only surfaces against an actual SQL
 * engine, so mocking the {@code CriteriaBuilder} would test interactions, not the query — these
 * tests assert the rows the query actually returns.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProducerSpecificationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private ProducerRepository producerRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private ProductCategoryRepository categoryRepository;
  @PersistenceContext private EntityManager em;

  private int seq = 0;

  /**
   * Persists a User + Producer. {@code farmers.created_at/updated_at} are mapped {@code
   * insertable=false} (prod fills them via a Flyway {@code DEFAULT now()}); under {@code
   * ddl-auto=create-drop} that default is absent, so the row is inserted natively with explicit
   * timestamps, then re-loaded as a managed entity.
   */
  private Producer newProducer(String farmName, String userName, boolean active, String rating,
      int totalOrders) {
    int n = ++seq;
    User user = new User();
    user.setName(userName);
    user.setEmail("user" + n + "@ragro.test");
    user.setType(TypeUser.FARMER);
    user.setActive(active);
    user.setAuthSub("sub-" + n);
    user = userRepository.save(user);

    em.createNativeQuery(
            "INSERT INTO farmers (id, fiscal_number, fiscal_number_type, farm_name, total_reviews,"
                + " average_rating, total_orders, total_sales_amount, created_at, updated_at) VALUES"
                + " (:id, :fn, 'CNPJ', :name, 0, :rating, :orders, 0, now(), now())")
        .setParameter("id", user.getId())
        .setParameter("fn", String.format("%014d", n))
        .setParameter("name", farmName)
        .setParameter("rating", new BigDecimal(rating))
        .setParameter("orders", totalOrders)
        .executeUpdate();
    em.flush();
    em.clear();
    return producerRepository.findById(user.getId()).orElseThrow();
  }

  private Product newProduct(Producer farmer, String name, ProductCategory... categories) {
    Product product = new Product();
    product.setFarmer(farmer);
    product.setName(name);
    product.setPrice(new BigDecimal("10.00"));
    for (ProductCategory c : categories) {
      product.getCategories().add(c);
    }
    return productRepository.save(product);
  }

  private ProductCategory newCategory(String name) {
    ProductCategory c = new ProductCategory();
    c.setName(name);
    return categoryRepository.save(c);
  }

  private ProducerFilter filter(String query, Double minRating, String sortBy) {
    ProducerFilter f = new ProducerFilter();
    f.setQuery(query);
    f.setMinRating(minRating);
    if (sortBy != null) {
      f.setSortBy(sortBy);
    }
    return f;
  }

  private List<Producer> find(ProducerFilter f) {
    return producerRepository.findAll(ProducerSpecification.withFilter(f));
  }

  @Test
  void excludesProducersWhoseUserIsInactive() {
    Producer active = newProducer("Fazenda Ativa", "Ana", true, "4.0", 0);
    newProducer("Fazenda Inativa", "Bruno", false, "5.0", 0);

    List<Producer> result = find(filter(null, null, null));

    assertThat(result).extracting(Producer::getId).containsExactly(active.getId());
  }

  @Test
  void blankQueryAppliesNoTextFilter() {
    Producer a = newProducer("Alpha", "Ana", true, "4.0", 0);
    Producer b = newProducer("Beta", "Bruno", true, "3.0", 0);

    List<Producer> result = find(filter("   ", null, null));

    assertThat(result).extracting(Producer::getId).containsExactlyInAnyOrder(a.getId(), b.getId());
  }

  @Test
  void queryMatchesFarmNameCaseInsensitively() {
    Producer match = newProducer("Sítio do Tomate", "Ana", true, "4.0", 0);
    newProducer("Outra Fazenda", "Bruno", true, "4.0", 0);

    List<Producer> result = find(filter("TOMATE", null, null));

    assertThat(result).extracting(Producer::getId).containsExactly(match.getId());
  }

  @Test
  void queryMatchesUserName() {
    Producer match = newProducer("Fazenda X", "Joana Silva", true, "4.0", 0);
    newProducer("Fazenda Y", "Carlos", true, "4.0", 0);

    List<Producer> result = find(filter("joana", null, null));

    assertThat(result).extracting(Producer::getId).containsExactly(match.getId());
  }

  @Test
  void queryMatchesProductNameViaSubquery() {
    Producer withProduct = newProducer("Fazenda Verde", "Ana", true, "4.0", 0);
    newProduct(withProduct, "Cenoura Orgânica");
    newProducer("Fazenda Azul", "Bruno", true, "4.0", 0);

    List<Producer> result = find(filter("cenoura", null, null));

    assertThat(result).extracting(Producer::getId).containsExactly(withProduct.getId());
  }

  @Test
  void queryMatchesCategoryNameViaSubquery() {
    ProductCategory legumes = newCategory("Legumes");
    Producer withCategory = newProducer("Fazenda Verde", "Ana", true, "4.0", 0);
    newProduct(withCategory, "Produto Sem Nome Relevante", legumes);
    newProducer("Fazenda Azul", "Bruno", true, "4.0", 0);

    List<Producer> result = find(filter("legumes", null, null));

    assertThat(result).extracting(Producer::getId).containsExactly(withCategory.getId());
  }

  @Test
  void minRatingExcludesLowerRatedProducers() {
    Producer high = newProducer("Top", "Ana", true, "4.50", 0);
    newProducer("Low", "Bruno", true, "3.00", 0);

    List<Producer> result = find(filter(null, 4.0, null));

    assertThat(result).extracting(Producer::getId).containsExactly(high.getId());
  }

  @Test
  void minRatingIsInclusiveAtTheBoundary() {
    Producer exact = newProducer("Boundary", "Ana", true, "4.00", 0);

    List<Producer> result = find(filter(null, 4.0, null));

    assertThat(result).extracting(Producer::getId).containsExactly(exact.getId());
  }

  @Test
  void sortByNameOrdersByFarmNameAscending() {
    Producer charlie = newProducer("Charlie", "U1", true, "1.0", 0);
    Producer alpha = newProducer("Alpha", "U2", true, "1.0", 0);
    Producer bravo = newProducer("Bravo", "U3", true, "1.0", 0);

    List<Producer> result = find(filter(null, null, "name"));

    assertThat(result).extracting(Producer::getId)
        .containsExactly(alpha.getId(), bravo.getId(), charlie.getId());
  }

  @Test
  void sortByOrdersOrdersByTotalOrdersDescending() {
    Producer few = newProducer("Few", "U1", true, "1.0", 2);
    Producer many = newProducer("Many", "U2", true, "1.0", 50);
    Producer mid = newProducer("Mid", "U3", true, "1.0", 10);

    List<Producer> result = find(filter(null, null, "orders"));

    assertThat(result).extracting(Producer::getId)
        .containsExactly(many.getId(), mid.getId(), few.getId());
  }

  @Test
  void defaultSortOrdersByAverageRatingDescending() {
    Producer low = newProducer("Low", "U1", true, "2.00", 0);
    Producer top = newProducer("Top", "U2", true, "5.00", 0);
    Producer mid = newProducer("Mid", "U3", true, "3.50", 0);

    List<Producer> result = find(filter(null, null, "rating"));

    assertThat(result).extracting(Producer::getId)
        .containsExactly(top.getId(), mid.getId(), low.getId());
  }

  @Test
  void countQueryUsesInnerJoinBranchAndStillPaginates() {
    newProducer("Fazenda A", "Ana", true, "4.0", 0);
    newProducer("Fazenda B", "Bruno", true, "4.0", 0);
    newProducer("Fazenda C", "Carla", false, "4.0", 0);

    // findAll(spec, pageable) runs a count query (resultType == Long), exercising the
    // root.join branch instead of the root.fetch branch, plus the active=true predicate.
    var page = producerRepository.findAll(
        ProducerSpecification.withFilter(filter(null, null, null)), PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent()).hasSize(2);
  }
}
