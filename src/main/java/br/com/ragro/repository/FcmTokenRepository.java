package br.com.ragro.repository;

import br.com.ragro.domain.FcmToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

    @Query("SELECT f.token FROM FcmToken f WHERE f.user.id = :userId")
    List<String> findTokensByUserId(@Param("userId") UUID userId);

    Optional<FcmToken> findByToken(String token);
}

