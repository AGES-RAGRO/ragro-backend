package br.com.ragro.repository;

import br.com.ragro.domain.Co2Saving;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Co2SavingRepository extends JpaRepository<Co2Saving, UUID> {
}
