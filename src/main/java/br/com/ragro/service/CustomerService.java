package br.com.ragro.service;

import br.com.ragro.controller.request.CustomerRequest;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.domain.Customer;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.mapper.CustomerMapper;
import br.com.ragro.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service   
public class CustomerService {

    private CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse updateCustomerById(UUID id, CustomerRequest request) {
    Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Cliente não encontrado"));

            if (!customer.getEmail().equals(request.getEmail()) && customerRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new BusinessException("E-mail já cadastrado"); 
            }

            customer.setName(request.getName());
            customer.setEmail(request.getEmail());
            customer.setPhone(request.getPhone());

            Customer updated = customerRepository.save(customer);
            return CustomerMapper.toResponse(updated);

}
}
