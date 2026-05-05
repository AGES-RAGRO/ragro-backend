package br.com.ragro.repository;

import br.com.ragro.domain.Review;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findAllByFarmerId(UUID farmerId, Pageable pageable);

    Optional<Review> findByOrderId(UUID orderId);   
    
}
