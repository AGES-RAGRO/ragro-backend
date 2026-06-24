package br.com.ragro.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ragro.controller.request.StockMovementFilter;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.StockMovement;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.StockMovementReason;
import br.com.ragro.domain.enums.StockMovementType;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.ProductRepository;
import br.com.ragro.repository.StockMovementRepository;
import br.com.ragro.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Real-Postgres ({@code @DataJpaTest}, auto-rollback) test of {@link StockMovementSpecification},
 * asserting which rows each filter combination returns and that results come back newest-first.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StockMovementSpecificationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private ProducerRepository producerRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private StockMovementRepository stockMovementRepository;
  @PersistenceContext private EntityManager em;

  private int seq = 0;

  private Producer newProducer() {
    int n = ++seq;
    User user = new User();
    user.setName("Farmer " + n);
    user.setEmail("user" + n + "@ragro.test");
    user.setType(TypeUser.FARMER);
    user.setActive(true);
    user.setAuthSub("sub-" + n);
    user = userRepository.save(user);

    em.createNativeQuery(
            "INSERT INTO farmers (id, fiscal_number, fiscal_number_type, farm_name, total_reviews,"
                + " average_rating, total_orders, total_sales_amount, created_at, updated_at) VALUES"
                + " (:id, :fn, 'CNPJ', 'Fazenda', 0, 0, 0, 0, now(), now())")
        .setParameter("id", user.getId())
        .setParameter("fn", String.format("%014d", n))
        .executeUpdate();
    em.flush();
    em.clear();
    return producerRepository.findById(user.getId()).orElseThrow();
  }

  private Product newProduct(Producer farmer, String name) {
    Product product = new Product();
    product.setFarmer(farmer);
    product.setName(name);
    product.setPrice(new BigDecimal("10.00"));
    return productRepository.save(product);
  }

  private StockMovement newMovement(Product product, StockMovementType type,
      StockMovementReason reason, OffsetDateTime createdAt) {
    StockMovement m = new StockMovement();
    m.setProduct(product);
    m.setType(type);
    m.setReason(reason);
    m.setQuantity(new BigDecimal("1.000"));
    m = stockMovementRepository.saveAndFlush(m);
    if (createdAt != null) {
      // created_at is @CreationTimestamp; overwrite natively to control ordering / range filters.
      em.createNativeQuery("UPDATE stock_movements SET created_at = :ts WHERE id = :id")
          .setParameter("ts", createdAt)
          .setParameter("id", m.getId())
          .executeUpdate();
      em.flush();
      em.clear();
    }
    return m;
  }

  private StockMovementFilter filter() {
    return new StockMovementFilter();
  }

  private List<StockMovement> find(UUID producerId, StockMovementFilter f) {
    return stockMovementRepository.findAll(StockMovementSpecification.withFilter(producerId, f));
  }

  private static OffsetDateTime at(int day) {
    return OffsetDateTime.of(2026, 1, day, 12, 0, 0, 0, ZoneOffset.UTC);
  }

  @Test
  void scopesMovementsToTheGivenProducer() {
    Producer mine = newProducer();
    Producer other = newProducer();
    Product mineProduct = newProduct(mine, "Tomate");
    Product otherProduct = newProduct(other, "Alface");
    StockMovement m = newMovement(mineProduct, StockMovementType.ENTRY,
        StockMovementReason.MANUAL_ENTRY, null);
    newMovement(otherProduct, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, null);

    List<StockMovement> result = find(mine.getId(), filter());

    assertThat(result).extracting(StockMovement::getId).containsExactly(m.getId());
  }

  @Test
  void filtersByProductId() {
    Producer producer = newProducer();
    Product a = newProduct(producer, "Tomate");
    Product b = newProduct(producer, "Alface");
    StockMovement target =
        newMovement(a, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, null);
    newMovement(b, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, null);

    StockMovementFilter f = filter();
    f.setProductId(a.getId());

    assertThat(find(producer.getId(), f)).extracting(StockMovement::getId)
        .containsExactly(target.getId());
  }

  @Test
  void filtersByReason() {
    Producer producer = newProducer();
    Product product = newProduct(producer, "Tomate");
    StockMovement loss =
        newMovement(product, StockMovementType.EXIT, StockMovementReason.LOSS, null);
    newMovement(product, StockMovementType.EXIT, StockMovementReason.SALE, null);

    StockMovementFilter f = filter();
    f.setReason(StockMovementReason.LOSS);

    assertThat(find(producer.getId(), f)).extracting(StockMovement::getId)
        .containsExactly(loss.getId());
  }

  @Test
  void filtersByType() {
    Producer producer = newProducer();
    Product product = newProduct(producer, "Tomate");
    StockMovement entry =
        newMovement(product, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, null);
    newMovement(product, StockMovementType.EXIT, StockMovementReason.SALE, null);

    StockMovementFilter f = filter();
    f.setType(StockMovementType.ENTRY);

    assertThat(find(producer.getId(), f)).extracting(StockMovement::getId)
        .containsExactly(entry.getId());
  }

  @Test
  void filtersByFromDateInclusive() {
    Producer producer = newProducer();
    Product product = newProduct(producer, "Tomate");
    StockMovement onBoundary =
        newMovement(product, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, at(10));
    newMovement(product, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, at(5));

    StockMovementFilter f = filter();
    f.setFrom(at(10));

    assertThat(find(producer.getId(), f)).extracting(StockMovement::getId)
        .containsExactly(onBoundary.getId());
  }

  @Test
  void filtersByToDateInclusive() {
    Producer producer = newProducer();
    Product product = newProduct(producer, "Tomate");
    StockMovement onBoundary =
        newMovement(product, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, at(10));
    newMovement(product, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, at(20));

    StockMovementFilter f = filter();
    f.setTo(at(10));

    assertThat(find(producer.getId(), f)).extracting(StockMovement::getId)
        .containsExactly(onBoundary.getId());
  }

  @Test
  void combinesFromAndToIntoADateWindow() {
    Producer producer = newProducer();
    Product product = newProduct(producer, "Tomate");
    StockMovement inside =
        newMovement(product, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, at(15));
    newMovement(product, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, at(5));
    newMovement(product, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, at(25));

    StockMovementFilter f = filter();
    f.setFrom(at(10));
    f.setTo(at(20));

    assertThat(find(producer.getId(), f)).extracting(StockMovement::getId)
        .containsExactly(inside.getId());
  }

  @Test
  void ordersByCreatedAtDescending() {
    Producer producer = newProducer();
    Product product = newProduct(producer, "Tomate");
    StockMovement oldest =
        newMovement(product, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, at(1));
    StockMovement newest =
        newMovement(product, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, at(28));
    StockMovement middle =
        newMovement(product, StockMovementType.ENTRY, StockMovementReason.MANUAL_ENTRY, at(14));

    assertThat(find(producer.getId(), filter())).extracting(StockMovement::getId)
        .containsExactly(newest.getId(), middle.getId(), oldest.getId());
  }
}
