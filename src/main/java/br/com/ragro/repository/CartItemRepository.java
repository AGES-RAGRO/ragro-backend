package br.com.ragro.repository;

import br.com.ragro.domain.CartItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
  Optional<CartItem> findByCartIdAndProductIdAndActiveTrue(UUID cartId, UUID productId);
  Optional<CartItem> findByCartIdAndIdAndActiveTrue(UUID cartId, UUID itemId);
}
