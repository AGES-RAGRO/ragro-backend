package br.com.ragro.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import br.com.ragro.domain.Customer;

import java.util.*;



public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional <Customer> findByEmail(String email);
}
import br.com.ragro.domain.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByFiscalNumber(String fiscalNumber);

  boolean existsByFiscalNumber(String fiscalNumber);
}
