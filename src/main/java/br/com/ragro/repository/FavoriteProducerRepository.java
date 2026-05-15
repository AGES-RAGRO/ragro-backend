package br.com.ragro.repository;

import br.com.ragro.domain.FavoriteProducer;
import br.com.ragro.domain.FavoriteProducerId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteProducerRepository
    extends JpaRepository<FavoriteProducer, FavoriteProducerId> {

  boolean existsByIdCustomerIdAndIdProducerId(UUID customerId, UUID producerId);

  long deleteByIdCustomerIdAndIdProducerId(UUID customerId, UUID producerId);

  @EntityGraph(attributePaths = {"producer", "producer.user"})
  List<FavoriteProducer> findByIdCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
