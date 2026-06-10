package br.com.ragro.repository;

import br.com.ragro.domain.RoutePosition;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoutePositionRepository extends JpaRepository<RoutePosition, UUID> {

  /** Purge da trilha além da janela de retenção (7 dias — decisão LGPD). */
  @Modifying
  @Query("DELETE FROM RoutePosition p WHERE p.recordedAt < :cutoff")
  int deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}
