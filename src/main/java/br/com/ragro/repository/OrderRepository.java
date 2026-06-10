package br.com.ragro.repository;

import br.com.ragro.domain.Order;
import br.com.ragro.domain.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

  Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

  // O grafo das listagens cobre só associações to-one (fetch join + paginação em coleção faria o
  // Hibernate paginar em memória); items/statusHistory continuam lazy, atenuados pelo
  // hibernate.default_batch_fetch_size.

  @EntityGraph(attributePaths = {"customer.user", "farmer.user", "paymentMethod"})
  Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

  @EntityGraph(attributePaths = {"customer.user", "farmer.user", "paymentMethod"})
  Page<Order> findByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"customer.user", "farmer.user", "paymentMethod"})
  Page<Order> findByFarmerId(UUID farmerId, Pageable pageable);

  @EntityGraph(attributePaths = {"customer.user", "farmer.user", "paymentMethod"})
  Page<Order> findByFarmerIdAndStatus(UUID farmerId, OrderStatus status, Pageable pageable);

  // ── Recommendation algorithm query ────────────────────────────

  @Query(
      """
      SELECT DISTINCT o.farmer.id
      FROM Order o
      WHERE o.customer.id = :customerId
        AND o.status <> br.com.ragro.domain.enums.OrderStatus.CANCELLED
      """)
  List<UUID> findDistinctFarmerIdsByCustomerId(@Param("customerId") UUID customerId);

  // ── Dashboard Queries ────────────────────────────────────────────────

  @Query(
      """
      SELECT COUNT(o)
      FROM Order o
      WHERE o.farmer.id = :farmerId
        AND o.status = br.com.ragro.domain.enums.OrderStatus.DELIVERED
        AND YEAR(o.deliveredAt) = :year
        AND MONTH(o.deliveredAt) = :month
      """)
  Long countDeliveredOrdersByMonth(
      @Param("farmerId") UUID farmerId, @Param("year") int year, @Param("month") int month);

  @Query(
      """
      SELECT COALESCE(SUM(oi.subtotal), 0)
      FROM Order o
      JOIN o.items oi
      WHERE o.farmer.id = :farmerId
        AND o.status = br.com.ragro.domain.enums.OrderStatus.DELIVERED
        AND YEAR(o.deliveredAt) = :year
        AND MONTH(o.deliveredAt) = :month
      """)
  BigDecimal sumDeliveredOrdersAmountByMonth(
      @Param("farmerId") UUID farmerId, @Param("year") int year, @Param("month") int month);

  @Query(
      """
      SELECT o
      FROM Order o
      WHERE o.farmer.id = :farmerId
        AND o.status = br.com.ragro.domain.enums.OrderStatus.DELIVERED
        AND o.deliveredAt >= :startDate
        AND o.deliveredAt < :endDate
      ORDER BY o.deliveredAt ASC
      """)
  List<Order> findDeliveredOrdersBetweenDates(
      @Param("farmerId") UUID farmerId,
      @Param("startDate") OffsetDateTime startDate,
      @Param("endDate") OffsetDateTime endDate);
}
