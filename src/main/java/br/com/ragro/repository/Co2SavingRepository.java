package br.com.ragro.repository;

import br.com.ragro.domain.Co2Saving;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface Co2SavingRepository extends JpaRepository<Co2Saving, UUID> {

    @Query("SELECT COALESCE(SUM(c.co2Saved), 0) FROM Co2Saving c")
Double sumAllCo2Saved();
}

