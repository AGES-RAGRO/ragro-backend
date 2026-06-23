package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ragro.domain.Address;
import br.com.ragro.domain.AddressSnapshot;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.PaymentMethod;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.PaymentStatus;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.PaymentMethodRepository;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test (real Postgres, committed transactions) proving fix #13: {@link
 * OrderService#confirmDeliveryWithCode} is {@code @Transactional(noRollbackFor =
 * BusinessException.class)} so the failed-attempt counter (and lockout) PERSISTS across wrong-code
 * attempts.
 *
 * <p>Before the fix the {@code saveAndFlush} that bumped {@code confirmationAttempts} was rolled
 * back by the {@code BusinessException} thrown immediately after, resetting the counter to 0 on
 * every request and defeating the 5-attempt / 15-minute anti-brute-force lockout.
 *
 * <p>The class is intentionally NOT {@code @Transactional}: each service call commits, and we
 * re-read in a fresh transaction so committed-vs-rolled-back state is observable — that is exactly
 * what distinguishes the fixed behavior from the regression.
 *
 * <p>{@code WebEnvironment.MOCK} (not NONE) is required: {@code SecurityConfig} wires {@code
 * MvcRequestMatcher}s, which need the MVC {@code mvcHandlerMappingIntrospector} bean. With NONE the
 * servlet/MVC context is absent and the security filter chain fails to instantiate at boot.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class OrderServiceConfirmDeliveryIntegrationTest {

  private static final String FARMER_SUB = "farmer-sub-it";
  private static final String FARMER_EMAIL = "farmer-it@ragro.test";
  private static final String CORRECT_CODE = "1234";

  @Autowired private OrderService orderService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ProducerRepository producerRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AddressRepository addressRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @PersistenceContext private EntityManager entityManager;

  // The class is deliberately NOT @Transactional, so each test commits its seed. Wipe everything
  // between tests so committed rows from a prior test don't collide on the unique email /
  // fiscal-number constraints. TRUNCATE ... CASCADE handles the order_status_history / order_items
  // children of orders in one shot, FK order be damned.
  @BeforeEach
  @AfterEach
  void cleanDatabase() {
    transactionTemplate.executeWithoutResult(
        status ->
            entityManager
                .createNativeQuery(
                    "TRUNCATE TABLE orders, payment_methods, addresses, customers, farmers, users"
                        + " RESTART IDENTITY CASCADE")
                .executeUpdate());
  }

  private UUID seedInDeliveryOrder() {
    // Seed the whole graph in ONE transaction so the @MapsId Producer/Customer see their User as a
    // managed (not detached) entity. The service calls under test then run in their own
    // transactions and re-read committed state.
    return transactionTemplate.execute(
        status -> {
          // Farmer User + Producer (Producer @MapsId → producer.id == user.id, which is what
          // loadOrderOwnedByFarmer compares against the authenticated user's id).
          User farmerUser = new User();
          farmerUser.setName("Farmer IT");
          farmerUser.setEmail(FARMER_EMAIL);
          farmerUser.setType(TypeUser.FARMER);
          farmerUser.setActive(true);
          farmerUser.setAuthSub(FARMER_SUB);
          farmerUser = userRepository.save(farmerUser);

          // Producer.created_at/updated_at are mapped insertable=false (the prod schema fills them
          // via a DB `DEFAULT now()` that Flyway creates). Under ddl-auto=create-drop that default
          // does NOT exist, so the JPA insert would violate the NOT NULL columns. Insert the
          // farmers row natively with the timestamps, then load the managed Producer.
          UUID farmerId = farmerUser.getId();
          entityManager
              .createNativeQuery(
                  "INSERT INTO farmers (id, fiscal_number, fiscal_number_type, farm_name,"
                      + " total_reviews, average_rating, total_orders, total_sales_amount,"
                      + " created_at, updated_at) VALUES (:id, :fn, :fnt, :name, 0, 0, 0, 0, now(),"
                      + " now())")
              .setParameter("id", farmerId)
              .setParameter("fn", "12345678000199")
              .setParameter("fnt", "CNPJ")
              .setParameter("name", "Fazenda IT")
              .executeUpdate();
          entityManager.flush();
          entityManager.clear();
          Producer farmer = producerRepository.findById(farmerId).orElseThrow();

          // Customer User + Customer.
          User customerUser = new User();
          customerUser.setName("Customer IT");
          customerUser.setEmail("customer-it@ragro.test");
          customerUser.setType(TypeUser.CUSTOMER);
          customerUser.setActive(true);
          customerUser.setAuthSub("customer-sub-it");
          customerUser = userRepository.save(customerUser);

          Customer customer = new Customer();
          customer.setUser(customerUser);
          customer.setFiscalNumber("12345678901");
          customer = customerRepository.save(customer);

          // Delivery address for the customer.
          Address address = new Address();
          address.setUser(customerUser);
          address.setStreet("Rua Teste");
          address.setNumber("100");
          address.setNeighborhood("Centro");
          address.setCity("Cidade");
          address.setState("SP");
          address.setZipCode("01001000");
          address.setPrimary(true);
          address = addressRepository.save(address);

          // Active payment method for the farmer.
          PaymentMethod paymentMethod = new PaymentMethod();
          paymentMethod.setFarmer(farmer);
          paymentMethod.setType("PIX");
          paymentMethod.setActive(true);
          paymentMethod = paymentMethodRepository.save(paymentMethod);

          // Order: IN_DELIVERY, code "1234", attempts 0, snapshot + all NOT NULL FKs set.
          Order order = new Order();
          order.setCustomer(customer);
          order.setFarmer(farmer);
          order.setDeliveryAddress(address);
          order.setDeliveryAddressSnapshot(
              AddressSnapshot.builder()
                  .street(address.getStreet())
                  .number(address.getNumber())
                  .neighborhood(address.getNeighborhood())
                  .city(address.getCity())
                  .state(address.getState())
                  .zipCode(address.getZipCode())
                  .latitude(BigDecimal.ZERO)
                  .longitude(BigDecimal.ZERO)
                  .build());
          order.setPaymentMethod(paymentMethod);
          order.setStatus(OrderStatus.IN_DELIVERY);
          order.setPaymentStatus(PaymentStatus.PENDING);
          order.setConfirmationCode(CORRECT_CODE);
          order.setConfirmationAttempts(0);
          order = orderRepository.save(order);

          return order.getId();
        });
  }

  private Jwt farmerJwt() {
    return Jwt.withTokenValue("t")
        .header("alg", "none")
        .subject(FARMER_SUB)
        .claim("email", FARMER_EMAIL)
        .build();
  }

  @Test
  void wrongCodeAttempts_persistAcrossCalls_provingNoRollbackForBusinessException() {
    UUID orderId = seedInDeliveryOrder();
    Jwt jwt = farmerJwt();

    // First wrong attempt → BusinessException, but the bumped counter must be COMMITTED.
    assertThatThrownBy(() -> orderService.confirmDeliveryWithCode(orderId, "0000", jwt))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Código de confirmação incorreto");

    Order afterFirst = orderRepository.findById(orderId).orElseThrow();
    assertThat(afterFirst.getConfirmationAttempts())
        .as("counter must persist after a wrong attempt (was reset to 0 before the fix)")
        .isEqualTo(1);

    // Second wrong attempt → counter must reach 2 (proves it accumulated, not reset to 0).
    assertThatThrownBy(() -> orderService.confirmDeliveryWithCode(orderId, "0000", jwt))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Código de confirmação incorreto");

    Order afterSecond = orderRepository.findById(orderId).orElseThrow();
    assertThat(afterSecond.getConfirmationAttempts()).isEqualTo(2);
  }

  @Test
  void afterFiveWrongAttempts_orderIsLockedAndFurtherCallsReportLockout() {
    UUID orderId = seedInDeliveryOrder();
    Jwt jwt = farmerJwt();

    // Attempts 1..4 → "incorreto".
    for (int i = 1; i <= 4; i++) {
      assertThatThrownBy(() -> orderService.confirmDeliveryWithCode(orderId, "0000", jwt))
          .isInstanceOf(BusinessException.class)
          .hasMessage("Código de confirmação incorreto");
    }

    // Attempt 5 → trips the lockout with the "bloqueado" message and sets the lock timestamp.
    assertThatThrownBy(() -> orderService.confirmDeliveryWithCode(orderId, "0000", jwt))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("bloqueado");

    Order locked = orderRepository.findById(orderId).orElseThrow();
    assertThat(locked.getConfirmationAttempts()).isEqualTo(5);
    assertThat(locked.getConfirmationLockedUntil())
        .as("lockout timestamp must be persisted")
        .isNotNull();

    // A further call while locked reports the throttle message.
    assertThatThrownBy(() -> orderService.confirmDeliveryWithCode(orderId, "0000", jwt))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Tente novamente");
  }

  @Test
  void correctCode_deliversOrderAndResetsAttempts() {
    UUID orderId = seedInDeliveryOrder();
    Jwt jwt = farmerJwt();

    // One wrong attempt first so the reset-on-success path is exercised.
    assertThatThrownBy(() -> orderService.confirmDeliveryWithCode(orderId, "0000", jwt))
        .isInstanceOf(BusinessException.class);

    orderService.confirmDeliveryWithCode(orderId, CORRECT_CODE, jwt);

    Order delivered = orderRepository.findById(orderId).orElseThrow();
    assertThat(delivered.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    assertThat(delivered.getConfirmationAttempts()).isZero();
    assertThat(delivered.getConfirmationLockedUntil()).isNull();
    assertThat(delivered.getDeliveredAt()).isNotNull();
  }
}
