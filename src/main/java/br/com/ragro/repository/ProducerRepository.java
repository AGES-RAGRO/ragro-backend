package br.com.ragro.repository;

import br.com.ragro.domain.Producer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface ProducerRepository extends JpaRepository<Producer, UUID> {

    Optional<Producer> findByFiscalNumber(String fiscalNumber);

    boolean existsByFiscalNumber(String fiscalNumber);

    @EntityGraph(attributePaths = {"user", "user.addresses"})
    @Query("SELECT p FROM Producer p ORDER BY p.averageRating DESC")
    Page<Producer> findAllUsersSortedByRating(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.addresses"})
    Optional<Producer> findDetailedById(UUID id);
}
