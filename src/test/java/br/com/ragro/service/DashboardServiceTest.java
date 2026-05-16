package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.response.DailySalesResponse;
import br.com.ragro.controller.response.DashboardMetricResponse;
import br.com.ragro.controller.response.DashboardWeeklyResponse;
import br.com.ragro.controller.response.ProducerDashboardResponse;
import br.com.ragro.controller.response.ProducerWeeklySalesResponse;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.OrderItem;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.Product;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.ProductRepository;
import br.com.ragro.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private ProductRepository productRepository;
  @Mock private StockMovementRepository stockMovementRepository;

  @InjectMocks private DashboardService dashboardService;

  private UUID producerId;
  private UUID farmerId;
  private Producer farmer;

  @BeforeEach
  void setUp() {
    producerId = UUID.randomUUID();
    farmerId = UUID.randomUUID();

    farmer = new Producer();
    farmer.setId(farmerId);
    farmer.setFarmName("Test Farm");

    // Lenient mocking for flexible repository calls
    lenient().when(orderRepository.countDeliveredOrdersByMonth(any(), anyInt(), anyInt())).thenReturn(0L);
    lenient().when(orderRepository.sumDeliveredOrdersAmountByMonth(any(), anyInt(), anyInt())).thenReturn(BigDecimal.ZERO);
    lenient().when(productRepository.findAllByFarmerId(any())).thenReturn(new ArrayList<>());
    lenient().when(stockMovementRepository.sumSoldStockByMonth(any(), anyInt(), anyInt())).thenReturn(BigDecimal.ZERO);
    lenient().when(stockMovementRepository.sumAddedStockByMonth(any(), anyInt(), anyInt())).thenReturn(BigDecimal.ZERO);
  }

  // ========== getProducerDashboard Tests ==========

  @Test
  void shouldReturnDashboard_withCurrentMonthByDefault() {
    LocalDate today = LocalDate.now();
    int currentMonth = today.getMonthValue();
    int currentYear = today.getYear();

    when(orderRepository.countDeliveredOrdersByMonth(eq(producerId), eq(currentYear), eq(currentMonth)))
        .thenReturn(5L);
    when(orderRepository.sumDeliveredOrdersAmountByMonth(eq(producerId), eq(currentYear), eq(currentMonth)))
        .thenReturn(new BigDecimal("150.00"));
    when(productRepository.findAllByFarmerId(eq(producerId)))
        .thenReturn(List.of(createProduct(new BigDecimal("100.00"))));
    when(stockMovementRepository.sumSoldStockByMonth(eq(producerId), eq(currentYear), eq(currentMonth)))
        .thenReturn(new BigDecimal("20.00"));
    when(stockMovementRepository.sumAddedStockByMonth(eq(producerId), eq(currentYear), eq(currentMonth)))
        .thenReturn(BigDecimal.ZERO);

    ProducerDashboardResponse response = dashboardService.getProducerDashboard(producerId, null, null);

    assertThat(response).isNotNull();
    assertThat(response.getMonth()).isEqualTo(currentMonth);
    assertThat(response.getYear()).isEqualTo(currentYear);
    assertThat(response.getOrdersMetric().getCurrentValue()).isEqualByComparingTo("5");
    assertThat(response.getSalesMetric().getCurrentValue()).isEqualByComparingTo("150.00");
  }

  @Test
  void shouldReturnDashboard_withSpecificMonthAndYear() {
    int month = 3;
    int year = 2024;

    when(orderRepository.countDeliveredOrdersByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(10L);
    when(orderRepository.sumDeliveredOrdersAmountByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(new BigDecimal("500.00"));
    when(productRepository.findAllByFarmerId(eq(producerId)))
        .thenReturn(List.of(createProduct(new BigDecimal("200.00"))));
    when(stockMovementRepository.sumSoldStockByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(new BigDecimal("50.00"));
    when(stockMovementRepository.sumAddedStockByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(new BigDecimal("10.00"));

    ProducerDashboardResponse response = dashboardService.getProducerDashboard(producerId, month, year);

    assertThat(response).isNotNull();
    assertThat(response.getMonth()).isEqualTo(month);
    assertThat(response.getYear()).isEqualTo(year);
    assertThat(response.getOrdersMetric().getCurrentValue()).isEqualByComparingTo("10");
    assertThat(response.getSalesMetric().getCurrentValue()).isEqualByComparingTo("500.00");
  }

  @Test
  void shouldCalculatePreviousMonthMetrics() {
    int month = 3;
    int year = 2024;
    int previousMonth = 2;
    int previousYear = 2024;

    when(orderRepository.countDeliveredOrdersByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(10L);
    when(orderRepository.sumDeliveredOrdersAmountByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(new BigDecimal("500.00"));
    when(orderRepository.countDeliveredOrdersByMonth(eq(producerId), eq(previousYear), eq(previousMonth)))
        .thenReturn(5L);
    when(orderRepository.sumDeliveredOrdersAmountByMonth(eq(producerId), eq(previousYear), eq(previousMonth)))
        .thenReturn(new BigDecimal("250.00"));
    when(productRepository.findAllByFarmerId(eq(producerId)))
        .thenReturn(List.of(createProduct(new BigDecimal("200.00"))));
    when(stockMovementRepository.sumSoldStockByMonth(any(), anyInt(), anyInt()))
        .thenReturn(BigDecimal.ZERO);

    ProducerDashboardResponse response = dashboardService.getProducerDashboard(producerId, month, year);

    DashboardMetricResponse ordersMetric = response.getOrdersMetric();
    assertThat(ordersMetric.getCurrentValue()).isEqualByComparingTo("10");
    assertThat(ordersMetric.getPreviousValue()).isEqualByComparingTo("5");
    assertThat(ordersMetric.getPercentageChange()).isEqualByComparingTo("100.00");

    DashboardMetricResponse salesMetric = response.getSalesMetric();
    assertThat(salesMetric.getCurrentValue()).isEqualByComparingTo("500.00");
    assertThat(salesMetric.getPreviousValue()).isEqualByComparingTo("250.00");
    assertThat(salesMetric.getPercentageChange()).isEqualByComparingTo("100.00");
  }

  @Test
  void shouldCalculatePercentageChangeCorrectly_fromZeroToPrevious() {
    int month = 3;
    int year = 2024;
    int previousMonth = 2;
    int previousYear = 2024;

    when(orderRepository.countDeliveredOrdersByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(5L);
    when(orderRepository.sumDeliveredOrdersAmountByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(new BigDecimal("100.00"));
    when(orderRepository.countDeliveredOrdersByMonth(eq(producerId), eq(previousYear), eq(previousMonth)))
        .thenReturn(0L);
    when(orderRepository.sumDeliveredOrdersAmountByMonth(eq(producerId), eq(previousYear), eq(previousMonth)))
        .thenReturn(BigDecimal.ZERO);
    when(productRepository.findAllByFarmerId(eq(producerId)))
        .thenReturn(List.of(createProduct(new BigDecimal("200.00"))));
    when(stockMovementRepository.sumSoldStockByMonth(any(), anyInt(), anyInt()))
        .thenReturn(BigDecimal.ZERO);

    ProducerDashboardResponse response = dashboardService.getProducerDashboard(producerId, month, year);

    DashboardMetricResponse ordersMetric = response.getOrdersMetric();
    assertThat(ordersMetric.getPercentageChange()).isEqualByComparingTo("100.00");

    DashboardMetricResponse salesMetric = response.getSalesMetric();
    assertThat(salesMetric.getPercentageChange()).isEqualByComparingTo("100.00");
  }

  @Test
  void shouldCalculatePercentageChangeCorrectly_decreaseFromPrevious() {
    int month = 3;
    int year = 2024;
    int previousMonth = 2;
    int previousYear = 2024;

    when(orderRepository.countDeliveredOrdersByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(5L);
    when(orderRepository.sumDeliveredOrdersAmountByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(new BigDecimal("250.00"));
    when(orderRepository.countDeliveredOrdersByMonth(eq(producerId), eq(previousYear), eq(previousMonth)))
        .thenReturn(10L);
    when(orderRepository.sumDeliveredOrdersAmountByMonth(eq(producerId), eq(previousYear), eq(previousMonth)))
        .thenReturn(new BigDecimal("500.00"));
    when(productRepository.findAllByFarmerId(eq(producerId)))
        .thenReturn(List.of(createProduct(new BigDecimal("200.00"))));
    when(stockMovementRepository.sumSoldStockByMonth(any(), anyInt(), anyInt()))
        .thenReturn(BigDecimal.ZERO);

    ProducerDashboardResponse response = dashboardService.getProducerDashboard(producerId, month, year);

    DashboardMetricResponse salesMetric = response.getSalesMetric();
    assertThat(salesMetric.getPercentageChange()).isEqualByComparingTo("-50.00");
  }

  @Test
  void shouldCalculateStockSoldPercentage_correctly() {
    int month = 3;
    int year = 2024;

    Product product1 = createProduct(new BigDecimal("100.00"));
    Product product2 = createProduct(new BigDecimal("150.00"));

    when(orderRepository.countDeliveredOrdersByMonth(eq(producerId), anyInt(), anyInt()))
        .thenReturn(0L);
    when(orderRepository.sumDeliveredOrdersAmountByMonth(eq(producerId), anyInt(), anyInt()))
        .thenReturn(BigDecimal.ZERO);
    when(productRepository.findAllByFarmerId(eq(producerId)))
        .thenReturn(List.of(product1, product2));
    when(stockMovementRepository.sumSoldStockByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(new BigDecimal("100.00"));
    when(stockMovementRepository.sumAddedStockByMonth(eq(producerId), eq(year), eq(month)))
        .thenReturn(new BigDecimal("50.00"));

    // Total current stock = 100 + 150 = 250
    // Total sold = 100
    // Total added = 50
    // Total initial = 250 + 100 - 50 = 300
    // Percentage = 100 / 300 * 100 = 33.33%

    ProducerDashboardResponse response = dashboardService.getProducerDashboard(producerId, month, year);

    assertThat(response.getStockSoldPercentage()).isEqualByComparingTo("33.33");
  }

  @Test
  void shouldReturnZeroStockPercentage_whenNoProducts() {
    when(productRepository.findAllByFarmerId(eq(producerId)))
        .thenReturn(new ArrayList<>());

    ProducerDashboardResponse response = dashboardService.getProducerDashboard(producerId, 3, 2024);

    assertThat(response.getStockSoldPercentage()).isEqualByComparingTo("0.00");
  }

  @Test
  void shouldReturnZeroStockPercentage_whenInitialStockIsZero() {
    Product product = createProduct(BigDecimal.ZERO);

    when(productRepository.findAllByFarmerId(eq(producerId)))
        .thenReturn(List.of(product));
    when(stockMovementRepository.sumSoldStockByMonth(any(), anyInt(), anyInt()))
        .thenReturn(BigDecimal.ZERO);
    when(stockMovementRepository.sumAddedStockByMonth(any(), anyInt(), anyInt()))
        .thenReturn(BigDecimal.ZERO);

    ProducerDashboardResponse response = dashboardService.getProducerDashboard(producerId, 3, 2024);

    assertThat(response.getStockSoldPercentage()).isEqualByComparingTo("0.00");
  }

  @Test
  void shouldCapStockPercentage_at100() {
    Product product = createProduct(new BigDecimal("100.00"));

    when(orderRepository.countDeliveredOrdersByMonth(eq(producerId), anyInt(), anyInt()))
        .thenReturn(0L);
    when(orderRepository.sumDeliveredOrdersAmountByMonth(eq(producerId), anyInt(), anyInt()))
        .thenReturn(BigDecimal.ZERO);
    when(productRepository.findAllByFarmerId(eq(producerId)))
        .thenReturn(List.of(product));
    when(stockMovementRepository.sumSoldStockByMonth(any(), anyInt(), anyInt()))
        .thenReturn(new BigDecimal("300.00"));
    when(stockMovementRepository.sumAddedStockByMonth(any(), anyInt(), anyInt()))
        .thenReturn(new BigDecimal("100.00"));

    // Total current stock = 100
    // Total sold = 300
    // Total added = 100
    // Total initial = 100 + 300 - 100 = 300
    // Percentage = 300 / 300 * 100 = 100.00

    ProducerDashboardResponse response = dashboardService.getProducerDashboard(producerId, 3, 2024);

    assertThat(response.getStockSoldPercentage()).isEqualByComparingTo("100.00");
  }

  @Test
  void shouldHandleNullSalesAmounts_asZero() {
    when(orderRepository.countDeliveredOrdersByMonth(eq(producerId), anyInt(), anyInt()))
        .thenReturn(0L);
    when(orderRepository.sumDeliveredOrdersAmountByMonth(eq(producerId), anyInt(), anyInt()))
        .thenReturn(null);
    when(productRepository.findAllByFarmerId(eq(producerId)))
        .thenReturn(new ArrayList<>());

    ProducerDashboardResponse response = dashboardService.getProducerDashboard(producerId, 3, 2024);

    assertThat(response.getSalesMetric().getCurrentValue()).isEqualByComparingTo("0.00");
  }

  // ========== getWeeklyDashboard Tests ==========

  @Test
  void shouldReturnWeeklyDashboard_withLast7Days() {
    LocalDate today = LocalDate.now();
    LocalDate sevenDaysAgo = today.minusDays(6);

    List<Order> orders = createOrdersForWeek(sevenDaysAgo, today, producerId);

    when(orderRepository.findDeliveredOrdersBetweenDates(any(), any(), any()))
        .thenReturn(orders);

    DashboardWeeklyResponse response = dashboardService.getWeeklyDashboard(producerId);

    assertThat(response).isNotNull();
    assertThat(response.getWeekStartDate()).isEqualTo(sevenDaysAgo);
    assertThat(response.getWeekEndDate()).isEqualTo(today);
    assertThat(response.getDailySales()).hasSize(7);
  }

  @Test
  void shouldGroupOrdersByDay_correctly() {
    LocalDate today = LocalDate.now();
    LocalDate sevenDaysAgo = today.minusDays(6);

    List<Order> orders = new ArrayList<>();
    // 2 orders on day 1
    orders.add(createOrderWithDate(sevenDaysAgo, new BigDecimal("100.00")));
    orders.add(createOrderWithDate(sevenDaysAgo, new BigDecimal("50.00")));
    // 1 order on day 2
    orders.add(createOrderWithDate(sevenDaysAgo.plusDays(1), new BigDecimal("75.00")));

    when(orderRepository.findDeliveredOrdersBetweenDates(any(), any(), any()))
        .thenReturn(orders);

    DashboardWeeklyResponse response = dashboardService.getWeeklyDashboard(producerId);

    List<DailySalesResponse> dailySales = response.getDailySales();

    // First day should have 2 orders and 150.00 total
    assertThat(dailySales.get(0).getOrderCount()).isEqualTo(2);
    assertThat(dailySales.get(0).getSalesAmount()).isEqualByComparingTo("150.00");

    // Second day should have 1 order and 75.00 total
    assertThat(dailySales.get(1).getOrderCount()).isEqualTo(1);
    assertThat(dailySales.get(1).getSalesAmount()).isEqualByComparingTo("75.00");
  }

  @Test
  void shouldFormatDate_correctly() {
    LocalDate today = LocalDate.now();
    LocalDate sevenDaysAgo = today.minusDays(6);

    List<Order> orders = new ArrayList<>();
    orders.add(createOrderWithDate(sevenDaysAgo, new BigDecimal("100.00")));

    when(orderRepository.findDeliveredOrdersBetweenDates(any(), any(), any()))
        .thenReturn(orders);

    DashboardWeeklyResponse response = dashboardService.getWeeklyDashboard(producerId);

    DailySalesResponse firstDay = response.getDailySales().get(0);
    assertThat(firstDay.getFullDate()).isEqualTo(sevenDaysAgo);
    // Format should be dd/MM
    String[] parts = firstDay.getDate().split("/");
    assertThat(parts).hasSize(2);
  }

  @Test
  void shouldIncludeAllDays_evenWithoutOrders() {
    LocalDate today = LocalDate.now();
    LocalDate sevenDaysAgo = today.minusDays(6);

    List<Order> orders = new ArrayList<>();
    // Only add order for first day
    orders.add(createOrderWithDate(sevenDaysAgo, new BigDecimal("100.00")));

    when(orderRepository.findDeliveredOrdersBetweenDates(any(), any(), any()))
        .thenReturn(orders);

    DashboardWeeklyResponse response = dashboardService.getWeeklyDashboard(producerId);

    assertThat(response.getDailySales()).hasSize(7);
    // All 7 days should be present
    for (int i = 0; i < 7; i++) {
      assertThat(response.getDailySales().get(i).getFullDate())
          .isEqualTo(sevenDaysAgo.plusDays(i));
    }
  }

  @Test
  void shouldReturnZeroSales_forDaysWithoutOrders() {
    LocalDate today = LocalDate.now();
    LocalDate sevenDaysAgo = today.minusDays(6);

    when(orderRepository.findDeliveredOrdersBetweenDates(any(), any(), any()))
        .thenReturn(new ArrayList<>());

    DashboardWeeklyResponse response = dashboardService.getWeeklyDashboard(producerId);

    for (DailySalesResponse daily : response.getDailySales()) {
      assertThat(daily.getOrderCount()).isEqualTo(0);
      assertThat(daily.getSalesAmount()).isEqualByComparingTo("0.00");
    }
  }

  @Test
  void shouldCalculateSalesAmount_fromOrderItems() {
    LocalDate today = LocalDate.now();
    LocalDate sevenDaysAgo = today.minusDays(6);

    Order order = new Order();
    order.setDeliveredAt(sevenDaysAgo.atStartOfDay()
        .atZone(ZoneId.systemDefault())
        .toOffsetDateTime());
    order.setItems(new ArrayList<>());

    OrderItem item1 = new OrderItem();
    item1.setSubtotal(new BigDecimal("50.00"));
    OrderItem item2 = new OrderItem();
    item2.setSubtotal(new BigDecimal("30.00"));

    order.getItems().add(item1);
    order.getItems().add(item2);

    when(orderRepository.findDeliveredOrdersBetweenDates(any(), any(), any()))
        .thenReturn(List.of(order));

    DashboardWeeklyResponse response = dashboardService.getWeeklyDashboard(producerId);

    DailySalesResponse firstDay = response.getDailySales().get(0);
    assertThat(firstDay.getSalesAmount()).isEqualByComparingTo("80.00");
  }

  // ========== getWeeklySales Tests ==========

  @Test
  void shouldReturnWeeklySales_withExactly7DaysAndZeroesWhenNoSales() {
    LocalDate today = LocalDate.now();
    LocalDate sevenDaysAgo = today.minusDays(6);

    List<Order> orders = new ArrayList<>();
    orders.add(createOrderWithDate(sevenDaysAgo, BigDecimal.ZERO));
    orders.add(createOrderWithDate(sevenDaysAgo, BigDecimal.ZERO));
    orders.add(createOrderWithDate(sevenDaysAgo.plusDays(2), BigDecimal.ZERO));

    when(orderRepository.findDeliveredOrdersBetweenDates(any(), any(), any()))
        .thenReturn(orders);

    List<ProducerWeeklySalesResponse> response = dashboardService.getWeeklySales(producerId);

    assertThat(response).hasSize(7);
    assertThat(response.get(0).getDate()).isEqualTo(sevenDaysAgo);
    assertThat(response.get(0).getSalesCount()).isEqualTo(2);
    assertThat(response.get(1).getDate()).isEqualTo(sevenDaysAgo.plusDays(1));
    assertThat(response.get(1).getSalesCount()).isZero();
    assertThat(response.get(2).getDate()).isEqualTo(sevenDaysAgo.plusDays(2));
    assertThat(response.get(2).getSalesCount()).isEqualTo(1);
    assertThat(response.get(6).getDate()).isEqualTo(today);
    assertThat(response.get(6).getSalesCount()).isZero();
    assertThat(response).allSatisfy(day -> assertThat(day.getDayLabel()).isNotBlank());
  }

  // ========== Helper Methods ==========

  private Product createProduct(BigDecimal stockQuantity) {
    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setName("Test Product");
    product.setPrice(new BigDecimal("10.00"));
    product.setStockQuantity(stockQuantity);
    product.setFarmer(farmer);
    return product;
  }

  private Order createOrderWithDate(LocalDate date, BigDecimal subtotal) {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setFarmer(farmer);

    OffsetDateTime deliveredAt = date.atStartOfDay()
        .atZone(ZoneId.systemDefault())
        .toOffsetDateTime();
    order.setDeliveredAt(deliveredAt);

    OrderItem item = new OrderItem();
    item.setSubtotal(subtotal);

    order.setItems(new ArrayList<>());
    order.getItems().add(item);

    return order;
  }

  private List<Order> createOrdersForWeek(LocalDate start, LocalDate end, UUID producerId) {
    List<Order> orders = new ArrayList<>();
    LocalDate current = start;

    while (!current.isAfter(end)) {
      Order order = new Order();
      order.setId(UUID.randomUUID());
      order.setFarmer(farmer);

      OffsetDateTime deliveredAt = current.atStartOfDay()
          .atZone(ZoneId.systemDefault())
          .toOffsetDateTime();
      order.setDeliveredAt(deliveredAt);

      OrderItem item = new OrderItem();
      item.setSubtotal(new BigDecimal("100.00"));

      order.setItems(new ArrayList<>());
      order.getItems().add(item);

      orders.add(order);
      current = current.plusDays(1);
    }

    return orders;
  }
}




