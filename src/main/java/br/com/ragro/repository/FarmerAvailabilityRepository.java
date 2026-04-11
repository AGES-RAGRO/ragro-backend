package br.com.ragro.repository;

import br.com.ragro.domain.FarmerAvailability;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmerAvailabilityRepository extends JpaRepository<FarmerAvailability, UUID> {}