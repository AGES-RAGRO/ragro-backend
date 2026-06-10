package br.com.ragro.repository;

import br.com.ragro.domain.DeliveryRoute;
import br.com.ragro.domain.enums.DeliveryRouteStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRouteRepository extends JpaRepository<DeliveryRoute, UUID> {

  @EntityGraph(attributePaths = {"stops", "stops.order"})
  Optional<DeliveryRoute> findByFarmerIdAndStatus(UUID farmerId, DeliveryRouteStatus status);

  @EntityGraph(attributePaths = {"stops", "stops.order"})
  Optional<DeliveryRoute> findWithStopsById(UUID id);
}
