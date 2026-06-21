package br.com.ragro.repository;

import br.com.ragro.domain.Product;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  List<Product> findAllByFarmerId(UUID farmerId);

  @Query(
      """
      SELECT p
      FROM Product p
      JOIN p.farmer f
      JOIN f.user u
      WHERE p.farmer.id = :farmerId
        AND p.active = true
        AND u.active = true
      """)
  List<Product> findAllByFarmerIdAndActiveTrue(@Param("farmerId") UUID farmerId);

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

  @Query(
      """
      SELECT p
      FROM Product p
      JOIN p.farmer f
      JOIN f.user u
      WHERE p.id IN :ids
        AND p.active = true
        AND u.active = true
      """)
  List<Product> findAllByIdAndFarmerUserActiveTrue(@Param("ids") List<UUID> ids);

  // ── Recommendation algorithm queries ──────────────────────────

  @Query(
      """
      SELECT p
      FROM OrderItem oi
      JOIN oi.order o
      JOIN oi.product p
      JOIN p.farmer f
      JOIN f.user u
      WHERE o.createdAt >= :since
        AND o.status <> br.com.ragro.domain.enums.OrderStatus.CANCELLED
        AND p.active = true
        AND u.active = true
      GROUP BY p
      ORDER BY COUNT(oi) DESC
      """)
  Page<Product> findTrendingProducts(@Param("since") OffsetDateTime since, Pageable pageable);

  @Query(
      """
      SELECT p
      FROM Product p
      JOIN p.farmer f
      JOIN f.user u
      WHERE p.active = true
        AND u.active = true
      ORDER BY p.createdAt DESC
      """)
  Page<Product> findRecentActiveProducts(Pageable pageable);

  @Query(
      """
      SELECT DISTINCT p
      FROM Product p
      JOIN p.categories c
      JOIN p.farmer f
      JOIN f.user u
      WHERE p.active = true
        AND u.active = true
        AND c.id IN :categoryIds
      """)
  List<Product> findActiveProductsByCategoryIds(@Param("categoryIds") List<Integer> categoryIds);
}
