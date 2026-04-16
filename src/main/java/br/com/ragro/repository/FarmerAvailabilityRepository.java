package br.com.ragro.repository;

import br.com.ragro.domain.FarmerAvailability;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmerAvailabilityRepository extends JpaRepository<FarmerAvailability, UUID> {

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM FarmerAvailability fa WHERE fa.farmer.id = :farmerId")
	void deleteByFarmerId(@Param("farmerId") UUID farmerId);

	List<FarmerAvailability> findByFarmerIdAndActiveTrueOrderByWeekdayAsc(UUID farmerId);
}