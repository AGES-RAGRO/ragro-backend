package br.com.ragro.repository;

import br.com.ragro.domain.Cart;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {
  Optional<Cart> findByCustomerIdAndActiveTrue(UUID customerId);
}
