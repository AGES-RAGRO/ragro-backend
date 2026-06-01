package br.com.ragro.repository;

import br.com.ragro.domain.OrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

  // ── Recommendation algorithm queries ──────────────────────────

  @Query(
      """
      SELECT DISTINCT oi.product.id
      FROM OrderItem oi
      JOIN oi.order o
      WHERE o.customer.id = :customerId
        AND o.status <> br.com.ragro.domain.enums.OrderStatus.CANCELLED
      """)
  List<UUID> findDistinctProductIdsByCustomerId(@Param("customerId") UUID customerId);

  @Query(
      """
      SELECT DISTINCT oi2.product.id
      FROM OrderItem oi1
      JOIN oi1.order o
      JOIN OrderItem oi2 ON oi2.order = o
      WHERE o.customer.id = :customerId
        AND o.status <> br.com.ragro.domain.enums.OrderStatus.CANCELLED
        AND oi1.product.id IN :purchasedProductIds
        AND oi2.product.id NOT IN :purchasedProductIds
      """)
  List<UUID> findCoOccurringProductIds(
      @Param("customerId") UUID customerId,
      @Param("purchasedProductIds") List<UUID> purchasedProductIds);
}
