package br.com.ragro.repository;

import br.com.ragro.domain.PaymentMethod;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {}
