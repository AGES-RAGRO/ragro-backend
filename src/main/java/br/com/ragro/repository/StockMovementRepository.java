package br.com.ragro.repository;

import br.com.ragro.domain.StockMovement;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository
    extends JpaRepository<StockMovement, UUID>, JpaSpecificationExecutor<StockMovement> {

  List<StockMovement> findAllByProductId(UUID productId);

  Page<StockMovement> findAllByProductId(UUID productId, Pageable pageable);

  // ── Dashboard Queries ────────────────────────────────────────────────

  @Query(
      """
      SELECT COALESCE(SUM(sm.quantity), 0)
      FROM StockMovement sm
      JOIN sm.product p
      WHERE p.farmer.id = :farmerId
        AND sm.type = br.com.ragro.domain.enums.StockMovementType.EXIT
        AND sm.reason = br.com.ragro.domain.enums.StockMovementReason.SALE
        AND YEAR(sm.createdAt) = :year
        AND MONTH(sm.createdAt) = :month
      """)
  BigDecimal sumSoldStockByMonth(
      @Param("farmerId") UUID farmerId, @Param("year") int year, @Param("month") int month);

  @Query(
      """
      SELECT COALESCE(SUM(sm.quantity), 0)
      FROM StockMovement sm
      JOIN sm.product p
      WHERE p.farmer.id = :farmerId
        AND sm.type = br.com.ragro.domain.enums.StockMovementType.ENTRY
        AND (sm.reason = br.com.ragro.domain.enums.StockMovementReason.MANUAL_ENTRY
          OR sm.reason = br.com.ragro.domain.enums.StockMovementReason.CANCELED_SALE)
        AND YEAR(sm.createdAt) = :year
        AND MONTH(sm.createdAt) = :month
      """)
  BigDecimal sumAddedStockByMonth(
      @Param("farmerId") UUID farmerId, @Param("year") int year, @Param("month") int month);
}
