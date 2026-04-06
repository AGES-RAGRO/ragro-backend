package br.com.ragro.controller;

import br.com.ragro.controller.request.CustomerRequest;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.service.CustomerService;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/customers")

public class CustomerController {
   private final CustomerService customerService;

      public CustomerController(CustomerService customerService) {
         this.customerService = customerService;
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomerById(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerRequest request) {
        
        CustomerResponse response = customerService.updateCustomerById(id, request);
        return ResponseEntity.ok(response);
    }
   }


