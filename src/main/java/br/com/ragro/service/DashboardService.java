package br.com.ragro.service;

import br.com.ragro.controller.response.DailySalesResponse;
import br.com.ragro.controller.response.DashboardMetricResponse;
import br.com.ragro.controller.response.DashboardWeeklyResponse;
import br.com.ragro.controller.response.ProducerDashboardResponse;
import br.com.ragro.domain.Order;
import br.com.ragro.domain.Product;
import br.com.ragro.repository.OrderRepository;
import br.com.ragro.repository.ProductRepository;
import br.com.ragro.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final StockMovementRepository stockMovementRepository;

  @Transactional(readOnly = true)
  public ProducerDashboardResponse getProducerDashboard(
      UUID producerId, Integer month, Integer year) {
    YearMonth selectedMonth =
        month != null && year != null ? YearMonth.of(year, month) : YearMonth.now();

    int selectedYear = selectedMonth.getYear();
    int selectedMonthValue = selectedMonth.getMonthValue();

    YearMonth previousMonth = selectedMonth.minusMonths(1);

    long currentDeliveredOrders =
        orderRepository.countDeliveredOrdersByMonth(producerId, selectedYear, selectedMonthValue);
    BigDecimal currentSalesAmount =
        orderRepository.sumDeliveredOrdersAmountByMonth(
            producerId, selectedYear, selectedMonthValue);

    long previousDeliveredOrders =
        orderRepository.countDeliveredOrdersByMonth(
            producerId, previousMonth.getYear(), previousMonth.getMonthValue());
    BigDecimal previousSalesAmount =
        orderRepository.sumDeliveredOrdersAmountByMonth(
            producerId, previousMonth.getYear(), previousMonth.getMonthValue());

    BigDecimal stockSoldPercentage =
        calculateStockSoldPercentage(producerId, selectedYear, selectedMonthValue);
    BigDecimal previousStockSoldPercentage =
        calculateStockSoldPercentage(
            producerId, previousMonth.getYear(), previousMonth.getMonthValue());

    DashboardMetricResponse ordersMetric =
        DashboardMetricResponse.builder()
            .currentValue(BigDecimal.valueOf(currentDeliveredOrders))
            .previousValue(BigDecimal.valueOf(previousDeliveredOrders))
            .percentageChange(
                calculatePercentageChange(
                    BigDecimal.valueOf(previousDeliveredOrders),
                    BigDecimal.valueOf(currentDeliveredOrders)))
            .build();

    DashboardMetricResponse salesMetric =
        DashboardMetricResponse.builder()
            .currentValue(currentSalesAmount != null ? currentSalesAmount : BigDecimal.ZERO)
            .previousValue(previousSalesAmount != null ? previousSalesAmount : BigDecimal.ZERO)
            .percentageChange(calculatePercentageChange(previousSalesAmount, currentSalesAmount))
            .build();

    DashboardMetricResponse stockMetric =
        DashboardMetricResponse.builder()
            .currentValue(stockSoldPercentage)
            .previousValue(previousStockSoldPercentage)
            .percentageChange(
                calculatePercentageChange(previousStockSoldPercentage, stockSoldPercentage))
            .build();

    return ProducerDashboardResponse.builder()
        .month(selectedMonthValue)
        .year(selectedYear)
        .salesMetric(salesMetric)
        .ordersMetric(ordersMetric)
        .stockSoldPercentage(stockSoldPercentage)
        .stockMetric(stockMetric)
        .build();
  }

  @Transactional(readOnly = true)
  public DashboardWeeklyResponse getWeeklyDashboard(UUID producerId) {
    ZoneId zoneId = ZoneId.systemDefault();
    LocalDate today = LocalDate.now();
    LocalDate sevenDaysAgo = today.minusDays(6);

    OffsetDateTime startDateTime = sevenDaysAgo.atStartOfDay().atZone(zoneId).toOffsetDateTime();
    OffsetDateTime endDateTime = today.plusDays(1).atStartOfDay().atZone(zoneId).toOffsetDateTime();

    List<Order> orders =
        orderRepository.findDeliveredOrdersBetweenDates(producerId, startDateTime, endDateTime);

    Map<LocalDate, List<Order>> ordersByDay =
        orders.stream()
            .collect(
                Collectors.groupingBy(
                    order -> order.getDeliveredAt().atZoneSameInstant(zoneId).toLocalDate()));

    List<DailySalesResponse> dailySales = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      LocalDate date = sevenDaysAgo.plusDays(i);
      List<Order> dayOrders = ordersByDay.getOrDefault(date, new ArrayList<>());

      long orderCount = dayOrders.size();
      BigDecimal salesAmount =
          dayOrders.stream()
              .flatMap(order -> order.getItems().stream())
              .map(br.com.ragro.domain.OrderItem::getSubtotal)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      String dayOfWeek =
          date.format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("pt-BR")));
      String formattedDate = date.format(DateTimeFormatter.ofPattern("dd/MM"));

      dailySales.add(
          DailySalesResponse.builder()
              .dayOfWeek(dayOfWeek)
              .date(formattedDate)
              .fullDate(date)
              .orderCount(orderCount)
              .salesAmount(salesAmount.setScale(2, RoundingMode.HALF_UP))
              .build());
    }

    return DashboardWeeklyResponse.builder()
        .weekStartDate(sevenDaysAgo)
        .weekEndDate(today)
        .dailySales(dailySales)
        .build();
  }

  private BigDecimal calculateStockSoldPercentage(UUID producerId, int year, int month) {
    List<Product> products = productRepository.findAllByFarmerId(producerId);

    if (products.isEmpty()) {
      return BigDecimal.ZERO;
    }

    BigDecimal totalCurrentStock =
        products.stream().map(Product::getStockQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalSoldQuantity =
        stockMovementRepository.sumSoldStockByMonth(producerId, year, month);
    BigDecimal totalAddedQuantity =
        stockMovementRepository.sumAddedStockByMonth(producerId, year, month);

    if (totalSoldQuantity == null) {
      totalSoldQuantity = BigDecimal.ZERO;
    }

    if (totalAddedQuantity == null) {
      totalAddedQuantity = BigDecimal.ZERO;
    }

    BigDecimal totalInitialStock =
        totalCurrentStock.add(totalSoldQuantity).subtract(totalAddedQuantity);

    if (totalInitialStock.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }

    BigDecimal percentage =
        totalSoldQuantity
            .divide(totalInitialStock, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);

    if (percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
      return BigDecimal.valueOf(100);
    }

    return percentage;
  }

  private BigDecimal calculatePercentageChange(BigDecimal previousValue, BigDecimal currentValue) {
    if (previousValue == null || previousValue.compareTo(BigDecimal.ZERO) == 0) {
      if (currentValue != null && currentValue.compareTo(BigDecimal.ZERO) > 0) {
        return BigDecimal.valueOf(100);
      }
      return BigDecimal.ZERO;
    }

    if (currentValue == null) {
      currentValue = BigDecimal.ZERO;
    }

    BigDecimal difference = currentValue.subtract(previousValue);
    return difference
        .divide(previousValue, 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .setScale(2, RoundingMode.HALF_UP);
  }
}
