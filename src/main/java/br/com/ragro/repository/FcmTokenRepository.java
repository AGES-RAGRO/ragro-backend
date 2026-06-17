package br.com.ragro.repository;

import br.com.ragro.domain.FcmToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

  @Query("SELECT f.token FROM FcmToken f WHERE f.user.id = :userId")
  List<String> findTokensByUserId(@Param("userId") UUID userId);

  Optional<FcmToken> findByToken(String token);

  @Transactional
  @Modifying
  @Query("DELETE FROM FcmToken f WHERE f.token IN :tokens")
  void deleteByTokenIn(@Param("tokens") Collection<String> tokens);
}
