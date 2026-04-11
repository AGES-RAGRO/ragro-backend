package br.com.ragro.repository;

import br.com.ragro.domain.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByFiscalNumber(String fiscalNumber);

  boolean existsByFiscalNumber(String fiscalNumber);

  @EntityGraph(attributePaths = {"user", "user.addresses"})
  Optional<Customer> findDetailedById(UUID id);
}
