package br.com.ragro.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.response.CustomerOrderResponse;
import br.com.ragro.controller.response.OrderResponse;
import br.com.ragro.domain.AddressSnapshot;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.OrderItem;
import br.com.ragro.domain.PaymentMethod;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.domain.ProductPhoto;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.OrderStatus;
import br.com.ragro.domain.enums.PaymentStatus;
import br.com.ragro.service.MinioStorageService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderMapperTest {

  private final MinioStorageService storage = mock(MinioStorageService.class);

  private User user(String name, String phone) {
    User u = new User();
    u.setId(UUID.randomUUID());
    u.setName(name);
    u.setPhone(phone);
    u.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    return u;
  }

  private Customer customer() {
    Customer c = new Customer();
    c.setUser(user("Maria", "51990000000"));
    c.setId(c.getUser().getId());
    return c;
  }

  private Producer producer(String phone) {
    Producer p = new Producer();
    p.setUser(user("João", phone));
    p.setId(p.getUser().getId());
    p.setFarmName("Fazenda São João");
    p.setAvatarS3("avatar-key");
    p.setDisplayPhotoS3("display-key");
    return p;
  }

  private Product productWithPhotos() {
    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setImageS3("legacy-key");
    ProductPhoto p2 = new ProductPhoto();
    p2.setUrl("photo-2");
    p2.setDisplayOrder((short) 2);
    ProductPhoto p1 = new ProductPhoto();
    p1.setUrl("photo-1");
    p1.setDisplayOrder((short) 1);
    product.getPhotos().add(p2);
    product.getPhotos().add(p1);
    return product;
  }

  private OrderItem item(Product product, String price) {
    OrderItem i = new OrderItem();
    i.setId(UUID.randomUUID());
    i.setProduct(product);
    i.setProductNameSnapshot("Tomate");
    i.setUnitPriceSnapshot(new BigDecimal(price));
    i.setUnityTypeSnapshot("kg");
    i.setQuantity(new BigDecimal("1"));
    i.setSubtotal(new BigDecimal(price));
    return i;
  }

  private PaymentMethod paymentMethod() {
    PaymentMethod pm = new PaymentMethod();
    pm.setId(UUID.randomUUID());
    pm.setType("bank_account");
    pm.setBankName("Banco do Brasil");
    pm.setAgency("1234");
    return pm;
  }

  private Order order(OrderStatus status) {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setCustomer(customer());
    order.setFarmer(producer("51988888888"));
    order.setPaymentMethod(paymentMethod());
    order.setStatus(status);
    order.setPaymentStatus(PaymentStatus.PENDING);
    order.setNotes("sem cebola");
    order.setSeenByFarmer(false);
    order.setCreatedAt(OffsetDateTime.parse("2026-02-02T00:00:00Z"));
    order.setConfirmationCode("4321");
    order.setCancellationReason("reason");
    order.setCancellationDetails("details");
    order.setDeliveryAddressSnapshot(AddressSnapshot.builder().street("Rua A").city("POA").build());
    List<OrderItem> items = new ArrayList<>();
    items.add(item(productWithPhotos(), "10.00"));
    items.add(item(productWithPhotos(), "5.00"));
    order.setItems(items);
    return order;
  }

  // ─── toResponse (producer-facing) ─────────────────────────────────────────

  @Test
  void toResponse_nullOrder_returnsNull() {
    assertThat(OrderMapper.toResponse(null, storage)).isNull();
  }

  @Test
  void toResponse_mapsAllFieldsAndSumsTotal() {
    Order order = order(OrderStatus.PENDING);

    OrderResponse r = OrderMapper.toResponse(order, null);

    assertThat(r.getId()).isEqualTo(order.getId());
    assertThat(r.getCustomerId()).isEqualTo(order.getCustomer().getId());
    assertThat(r.getCustomerName()).isEqualTo("Maria");
    assertThat(r.getCustomer().getName()).isEqualTo("Maria");
    assertThat(r.getCustomer().getPhone()).isEqualTo("51990000000");
    assertThat(r.getCustomer().getMemberSince()).isEqualTo(order.getCustomer().getUser().getCreatedAt());
    assertThat(r.getFarmerId()).isEqualTo(order.getFarmer().getId());
    assertThat(r.getFarmerName()).isEqualTo("Fazenda São João");
    assertThat(r.getDeliveryAddress()).isEqualTo(order.getDeliveryAddressSnapshot());
    assertThat(r.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(r.getPaymentMethodId()).isEqualTo(order.getPaymentMethod().getId());
    assertThat(r.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(r.getNotes()).isEqualTo("sem cebola");
    assertThat(r.getTotalAmount()).isEqualByComparingTo("15.00"); // 10 + 5
    assertThat(r.getCreatedAt()).isEqualTo(order.getCreatedAt());
    assertThat(r.getCancellationReason()).isEqualTo("reason");
    assertThat(r.getCancellationDetails()).isEqualTo("details");
    assertThat(r.getItems()).hasSize(2);
    // storage == null → raw key passes through
    assertThat(r.getItems().get(0).getProductPhoto()).isEqualTo("photo-1");
  }

  @Test
  void toResponse_isNew_trueOnlyWhenPendingAndNotSeen() {
    Order pendingUnseen = order(OrderStatus.PENDING);
    assertThat(OrderMapper.toResponse(pendingUnseen, null).getIsNew()).isTrue();

    Order pendingSeen = order(OrderStatus.PENDING);
    pendingSeen.setSeenByFarmer(true);
    assertThat(OrderMapper.toResponse(pendingSeen, null).getIsNew()).isFalse();

    Order confirmedUnseen = order(OrderStatus.CONFIRMED);
    assertThat(OrderMapper.toResponse(confirmedUnseen, null).getIsNew()).isFalse();
  }

  @Test
  void toResponse_resolvesItemPhotoUrlViaStorage() {
    when(storage.composePublicUrl("photo-1")).thenReturn("https://cdn/photo-1");

    OrderResponse r = OrderMapper.toResponse(order(OrderStatus.PENDING), storage);

    assertThat(r.getItems().get(0).getProductPhoto()).isEqualTo("https://cdn/photo-1");
  }

  // ─── toCustomerOrderResponse (consumer-facing) ────────────────────────────

  @Test
  void toCustomerOrderResponse_nullOrder_returnsNull() {
    assertThat(OrderMapper.toCustomerOrderResponse(null, storage, false)).isNull();
  }

  @Test
  void toCustomerOrderResponse_inDelivery_exposesCodeAndActions() {
    when(storage.composePublicUrl("avatar-key")).thenReturn("https://cdn/avatar-key");
    lenient().when(storage.composePublicUrl("photo-1")).thenReturn("https://cdn/photo-1");

    CustomerOrderResponse r =
        OrderMapper.toCustomerOrderResponse(order(OrderStatus.IN_DELIVERY), storage, true);

    assertThat(r.getId()).isNotNull();
    assertThat(r.getPrice()).isEqualByComparingTo("15.00");
    assertThat(r.getTotalAmount()).isEqualByComparingTo("15.00");
    assertThat(r.getProducerId()).isNotNull();
    assertThat(r.getProducerName()).isEqualTo("João");
    assertThat(r.getProducerPicture()).isEqualTo("https://cdn/avatar-key"); // avatar preferred
    assertThat(r.getProducerPhone()).isEqualTo("51988888888");
    assertThat(r.getStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
    assertThat(r.isReviewed()).isTrue();
    assertThat(r.getBankInfo()).isNotNull();
    assertThat(r.getBankInfo().getBankName()).isEqualTo("Banco do Brasil");
    assertThat(r.getConfirmationCode()).isEqualTo("4321"); // exposed only while IN_DELIVERY
    assertThat(r.getActions().isCanConfirmDelivery()).isTrue();
    assertThat(r.getActions().isCanCancel()).isTrue();
    assertThat(r.getActions().isCanContactProducer()).isTrue();
    assertThat(r.getItems()).hasSize(2);
  }

  @Test
  void toCustomerOrderResponse_delivered_hidesCodeAndDisablesActions() {
    CustomerOrderResponse r =
        OrderMapper.toCustomerOrderResponse(order(OrderStatus.DELIVERED), null, false);

    assertThat(r.getConfirmationCode()).isNull();
    assertThat(r.getActions().isCanConfirmDelivery()).isFalse();
    assertThat(r.getActions().isCanCancel()).isFalse();
    assertThat(r.getActions().isCanContactProducer()).isFalse(); // delivered → cannot contact
  }

  @Test
  void toCustomerOrderResponse_confirmed_allowsCancelButNotConfirm() {
    CustomerOrderResponse r =
        OrderMapper.toCustomerOrderResponse(order(OrderStatus.CONFIRMED), null, false);

    assertThat(r.getActions().isCanConfirmDelivery()).isFalse();
    assertThat(r.getActions().isCanCancel()).isTrue();
    assertThat(r.getActions().isCanContactProducer()).isTrue();
  }

  @Test
  void toCustomerOrderResponse_cancelled_cannotContactProducer() {
    CustomerOrderResponse r =
        OrderMapper.toCustomerOrderResponse(order(OrderStatus.CANCELLED), null, false);

    assertThat(r.getActions().isCanCancel()).isFalse();
    assertThat(r.getActions().isCanContactProducer()).isFalse();
  }

  @Test
  void toCustomerOrderResponse_blankProducerPhone_cannotContact() {
    Order order = order(OrderStatus.PENDING);
    order.getFarmer().getUser().setPhone("   ");

    CustomerOrderResponse r = OrderMapper.toCustomerOrderResponse(order, null, false);

    assertThat(r.getActions().isCanContactProducer()).isFalse();
  }

  // ─── photo resolution fallbacks ───────────────────────────────────────────

  @Test
  void producerPicture_fallsBackToDisplayPhoto_whenAvatarBlank() {
    Order order = order(OrderStatus.PENDING);
    order.getFarmer().setAvatarS3("  ");

    CustomerOrderResponse r = OrderMapper.toCustomerOrderResponse(order, null, false);

    assertThat(r.getProducerPicture()).isEqualTo("display-key");
  }

  @Test
  void productPhoto_fallsBackToImageS3_whenNoPhotos() {
    Order order = order(OrderStatus.PENDING);
    order.getItems().forEach(it -> it.getProduct().getPhotos().clear());

    OrderResponse r = OrderMapper.toResponse(order, null);

    assertThat(r.getItems().get(0).getProductPhoto()).isEqualTo("legacy-key");
  }

  @Test
  void productPhoto_skipsBlankUrlsAndPicksLowestDisplayOrder() {
    Order order = order(OrderStatus.PENDING);
    Product product = order.getItems().get(0).getProduct();
    product.getPhotos().clear();
    ProductPhoto blank = new ProductPhoto();
    blank.setUrl("  ");
    blank.setDisplayOrder((short) 0); // lowest order but blank → must be skipped
    ProductPhoto valid = new ProductPhoto();
    valid.setUrl("real.jpg");
    valid.setDisplayOrder((short) 3);
    product.getPhotos().add(blank);
    product.getPhotos().add(valid);

    OrderResponse r = OrderMapper.toResponse(order, null);

    assertThat(r.getItems().get(0).getProductPhoto()).isEqualTo("real.jpg");
  }

  @Test
  void productPhoto_nullWhenNoPhotosAndBlankImageS3() {
    Order order = order(OrderStatus.PENDING);
    OrderItem it = order.getItems().get(0);
    it.getProduct().getPhotos().clear();
    it.getProduct().setImageS3("  ");

    OrderResponse r = OrderMapper.toResponse(order, null);

    assertThat(r.getItems().get(0).getProductPhoto()).isNull();
  }
}
