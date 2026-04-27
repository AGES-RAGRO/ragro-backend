package br.com.ragro.repository;

import br.com.ragro.domain.StockMovement;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

  Page<StockMovement> findAllByProductId(UUID productId, Pageable pageable);
}