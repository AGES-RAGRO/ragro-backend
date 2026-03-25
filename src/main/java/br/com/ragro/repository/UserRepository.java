package br.com.ragro.repository;

import br.com.ragro.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByCognitoSub(String cognitoSub);

    boolean existsByEmail(@NotBlank @Email String email);

    boolean existsByCognitoSub(String cognitoSub);

    // Busca usuários por name ou email (excluindo um ID específico)
    @Query("""
        SELECT u FROM User u
        WHERE u.id <> :userId
        AND (
            LOWER(u.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        )
        """)
    Page<User> findByNameOrEmailContainingIgnoreCase(
            @Param("userId") UUID userId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable
    );
}
