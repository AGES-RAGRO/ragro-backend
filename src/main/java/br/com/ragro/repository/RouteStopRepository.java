package br.com.ragro.repository;

import br.com.ragro.domain.RouteStop;
import br.com.ragro.domain.enums.DeliveryRouteStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteStopRepository extends JpaRepository<RouteStop, UUID> {

  /** Tracking channel authorization: is the user the customer of an order on this route? */
  boolean existsByRouteIdAndOrderCustomerId(UUID routeId, UUID customerId);

  /** The order's stop on the producer's ACTIVE route (feeds the customer's tracking). */
  @Query(
      """
      SELECT s FROM RouteStop s
      JOIN FETCH s.route r
      WHERE s.order.id = :orderId
        AND r.status = :status
      """)
  Optional<RouteStop> findByOrderIdAndRouteStatus(
      @Param("orderId") UUID orderId, @Param("status") DeliveryRouteStatus status);
}
