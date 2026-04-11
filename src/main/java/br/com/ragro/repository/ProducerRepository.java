package br.com.ragro.repository;

import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface ProducerRepository extends JpaRepository<Producer, UUID> {

    Optional<Producer> findByFiscalNumber(String fiscalNumber);

    boolean existsByFiscalNumber(String fiscalNumber);

    @Query("SELECT p.user FROM Producer p ORDER BY p.averageRating DESC")
    Page<User> findAllUsersSortedByRating(Pageable pageable);
}
