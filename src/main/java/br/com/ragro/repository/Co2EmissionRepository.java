package br.com.ragro.repository;

import br.com.ragro.domain.Co2Emission;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Co2EmissionRepository extends JpaRepository<Co2Emission, UUID> {

  List<Co2Emission> findByVehiclePreferenceUserIdOrderByCreatedAtDesc(UUID userId);
}
