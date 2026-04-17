package br.com.ragro.repository;

import br.com.ragro.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  List<Product> findAllByFarmerId(UUID farmerId);

  Optional<Product> findByIdAndFarmerId(UUID id, UUID farmerId);
}
