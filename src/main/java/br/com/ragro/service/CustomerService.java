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
import br.com.ragro.controller.request.CustomerUpdateRequest;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.mapper.AddressMapper;
import br.com.ragro.mapper.CustomerMapper;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private final UserService userService;
  private final UserRepository userRepository;
  private final AddressRepository addressRepository;

  public CustomerService(
      UserService userService,
      UserRepository userRepository,
      AddressRepository addressRepository) {
    this.userService = userService;
    this.userRepository = userRepository;
    this.addressRepository = addressRepository;
  }

  @Transactional(readOnly = true)
  public CustomerResponse getMyCustomer(Jwt jwt) {
    User user = userService.getAuthenticatedUser(jwt);

    if (user.getType() != TypeUser.CUSTOMER) {
      throw new UnauthorizedException("Access restricted to customers");
    }

    return CustomerMapper.toResponse(user);
  }

  @Transactional
  public CustomerResponse updateMyCustomer(Jwt jwt, CustomerUpdateRequest request) {
    User user = userService.getAuthenticatedUser(jwt);

    if (user.getType() != TypeUser.CUSTOMER) {
      throw new UnauthorizedException("Access restricted to customers");
    }

    user.setName(request.getName().trim());
    user.setPhone(request.getPhone().trim());
    userRepository.save(user);

    Address primary =
    addressRepository
        .findByUserIdAndIsPrimaryTrue(user.getId())
        .orElseGet(() -> {
            Address newAddress = new Address();
            newAddress.setUser(user);
            newAddress.setPrimary(true);
            return newAddress;
        });
    AddressMapper.applyRequest(primary, request.getAddress());
    addressRepository.save(primary);

    User refreshed =
        userRepository
            .findById(user.getId())
            .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));
    return CustomerMapper.toResponse(refreshed);
  }
}
