package br.com.ragro.repository;

import br.com.ragro.domain.VehiclePreference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehiclePreferenceRepository extends JpaRepository<VehiclePreference, UUID> {
}
