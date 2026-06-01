package br.com.ragro.repository;

import br.com.ragro.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  @EntityGraph(attributePaths = "addresses")
  Optional<User> findByEmail(String email);

  @EntityGraph(attributePaths = "addresses")
  Optional<User> findByAuthSub(String authSub);

  boolean existsByEmail(@NotBlank @Email String email);
}
