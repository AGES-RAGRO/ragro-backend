package br.com.ragro.repository;

import br.com.ragro.domain.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

  List<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

  List<Order> findByFarmerIdOrderByCreatedAtDesc(UUID farmerId);

  // ── Query para o algoritmo de recomendação ────────────────────────────

  @Query(
      """
      SELECT DISTINCT o.farmer.id
      FROM Order o
      WHERE o.customer.id = :customerId
        AND o.status <> br.com.ragro.domain.enums.OrderStatus.CANCELLED
      """)
  List<UUID> findDistinctFarmerIdsByCustomerId(
      @Param("customerId") UUID customerId);
}