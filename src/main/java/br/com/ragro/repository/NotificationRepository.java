package br.com.ragro.repository;

import br.com.ragro.domain.Notification;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

  long countByUserIdAndReadFalse(UUID userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Notification n
         set n.read = true,
             n.readAt = :readAt
       where n.user.id = :userId
         and n.read = false
      """)
  int markAllAsReadByUserId(@Param("userId") UUID userId, @Param("readAt") OffsetDateTime readAt);
}
