package br.com.ragro.repository;

import br.com.ragro.domain.FavoriteProducer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FavoriteProducerRepository extends JpaRepository<FavoriteProducer, UUID> {

boolean existsByCustomerIdAndProducerId(
    UUID customerId,
    UUID producerId
);

void deleteByCustomerIdAndProducerId(
    UUID customerId,
    UUID producerId
);

List<FavoriteProducer> findByCustomerId(UUID customerId);
}
