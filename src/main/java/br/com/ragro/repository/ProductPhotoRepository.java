package br.com.ragro.repository;

import br.com.ragro.domain.ProductPhoto;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPhotoRepository extends JpaRepository<ProductPhoto, UUID> {}
