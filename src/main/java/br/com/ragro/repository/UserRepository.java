package br.com.ragro.repository;

import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  @EntityGraph(attributePaths = "addresses")
  Optional<User> findByEmail(String email);

  @EntityGraph(attributePaths = "addresses")
  Optional<User> findByAuthSub(String authSub);

  boolean existsByEmail(@NotBlank @Email String email);

  boolean existsByAuthSub(String authSub);

  List<User> findAllByType(TypeUser type);

  @Query(
      """
        SELECT u FROM User u
        WHERE u.id <> :userId
        AND (
            LOWER(u.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        )
        """)
  Page<User> findByNameOrEmailContainingIgnoreCase(
      @Param("userId") UUID userId, @Param("searchTerm") String searchTerm, Pageable pageable);
}
