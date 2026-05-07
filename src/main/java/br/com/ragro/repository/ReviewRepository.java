package br.com.ragro.repository;
import br.com.ragro.domain.Review;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByOrderId(UUID orderId);
    Page<Review> findAllByFarmerId(UUID farmerId, Pageable pageable);
 
}
