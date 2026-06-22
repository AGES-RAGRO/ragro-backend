package br.com.ragro.repository;

import br.com.ragro.domain.Address;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {

  Optional<Address> findByUserIdAndIsPrimaryTrue(UUID userId);
}
