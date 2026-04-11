package br.com.ragro.repository;

import br.com.ragro.domain.ProducerProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProducerProfileRepository extends JpaRepository<ProducerProfile, UUID> {}
