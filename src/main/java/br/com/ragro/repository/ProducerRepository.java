package br.com.ragro.repository;

import br.com.ragro.domain.Producer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProducerRepository extends JpaRepository<Producer, UUID> {
    Optional<Producer> findByFiscalNumber(String fiscalNumber);

    boolean existsByFiscalNumber(String fiscalNumber);
}
