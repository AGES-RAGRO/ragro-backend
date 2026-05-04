package br.com.ragro.repository;

import br.com.ragro.domain.Order;
import br.com.ragro.domain.enums.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

  List<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

  List<Order> findByFarmerIdOrderByCreatedAtDesc(UUID farmerId);

  List<Order> findByFarmerIdAndStatus(UUID farmerId, OrderStatus status);
}