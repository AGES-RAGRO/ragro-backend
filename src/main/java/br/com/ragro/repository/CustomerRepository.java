package br.com.ragro.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import br.com.ragro.domain.Customer;

import java.util.*;



public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional <Customer> findByEmail(String email);
}