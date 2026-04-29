package br.com.ragro.repository;

import br.com.ragro.domain.Producer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProducerRepository extends JpaRepository<Producer, UUID>,
    JpaSpecificationExecutor<Producer> {

    Optional<Producer> findByFiscalNumber(String fiscalNumber);

    boolean existsByFiscalNumber(String fiscalNumber);

    @EntityGraph(attributePaths = {"user", "user.addresses"})
    @Query("SELECT p FROM Producer p ORDER BY p.averageRating DESC")
    Page<Producer> findAllUsersSortedByRating(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.addresses"})
    @Query("SELECT p FROM Producer p WHERE p.user.active = true ORDER BY p.averageRating DESC")
    Page<Producer> findAllActiveSortedByRating(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.addresses"})
    Optional<Producer> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"user"})
    @Query(
        """
        SELECT DISTINCT producer
        FROM Producer producer
        JOIN producer.user user
        WHERE user.active = true
          AND (
            LOWER(producer.farmName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(user.name) LIKE LOWER(CONCAT('%', :query, '%'))
          )
          AND (
            :category IS NULL
            OR EXISTS (
              SELECT 1
              FROM Product product
              JOIN product.categories categoryEntity
              WHERE product.farmer = producer
                AND product.active = true
                AND LOWER(categoryEntity.name) = :category
            )
          )
        ORDER BY producer.averageRating DESC
        """)
    List<Producer> searchMarketplace(
        @Param("query") String query, @Param("category") String category);
}
