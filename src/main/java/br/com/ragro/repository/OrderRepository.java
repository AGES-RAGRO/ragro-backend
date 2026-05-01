package br.com.ragro.repository;

import br.com.ragro.domain.Order;
import java.util.UUID;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);
}