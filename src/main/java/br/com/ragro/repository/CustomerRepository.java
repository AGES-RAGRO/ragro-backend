package br.com.ragro.repository;

import br.com.ragro.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByFiscalNumber(String fiscalNumber);
}
