package br.com.ragro.repository;

import java.util.Optional;
import java.util.UUID;
import br.com.ragro.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByOrderId(UUID orderId);
    Page<Review> findAllByFarmerId(UUID farmerId, Pageable pageable);
    long countByFarmer_Id(UUID farmerId);

    @Query("select avg(r.rating) from Review r where r.farmer.id = :farmerId")
    Double findAverageRatingByFarmerId(UUID farmerId);
}
