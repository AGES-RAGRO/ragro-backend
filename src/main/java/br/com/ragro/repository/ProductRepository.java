package br.com.ragro.repository;

import br.com.ragro.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  List<Product> findAllByFarmerId(UUID farmerId);

  List<Product> findAllByFarmerIdAndActiveTrue(UUID farmerId);

  Optional<Product> findByIdAndFarmerId(UUID id, UUID farmerId);

  @EntityGraph(attributePaths = {"farmer", "farmer.user", "categories"})
  @Query(
      """
      SELECT DISTINCT product
      FROM Product product
      JOIN product.farmer farmer
      JOIN farmer.user user
      LEFT JOIN product.categories categoryEntity
      WHERE product.active = true
        AND user.active = true
        AND (
          LOWER(product.name) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(COALESCE(product.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
        )
        AND (
          :category IS NULL
          OR LOWER(categoryEntity.name) = :category
        )
      ORDER BY product.name ASC
      """)
  List<Product> searchActiveMarketplaceProducts(
      @Param("query") String query, @Param("category") String category);
}
