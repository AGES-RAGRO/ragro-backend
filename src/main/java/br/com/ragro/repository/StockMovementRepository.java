package br.com.ragro.repository;

import br.com.ragro.domain.StockMovement;
import br.com.ragro.domain.enums.StockMovementReason;
import br.com.ragro.domain.enums.StockMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID>{

    List<StockMovement> findAllByProductId(UUID productId);

    List<StockMovement> findAllByProductIdAndType(UUID productId, StockMovementType type);

     List<StockMovement> findAllByProductIdAndReason(UUID productId, StockMovementReason reason);

}
