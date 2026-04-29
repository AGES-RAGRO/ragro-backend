package br.com.ragro.repository;

import br.com.ragro.domain.StockMovement;
import br.com.ragro.domain.enums.StockMovementReason;
import br.com.ragro.domain.enums.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID>, JpaSpecificationExecutor<StockMovement> {

    List<StockMovement> findAllByProductId(UUID productId);

    Page<StockMovement> findAllByProductId(UUID productId, Pageable pageable);

    List<StockMovement> findAllByProductIdAndType(UUID productId, StockMovementType type);

    List<StockMovement> findAllByProductIdAndReason(UUID productId, StockMovementReason reason);

}
