package br.com.ragro.mapper;

import br.com.ragro.controller.response.CustomerResponse;  
import br.com.ragro.domain.Customer;

public class CustomerMapper {

    public static CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .active(customer.isActive())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
    
}
