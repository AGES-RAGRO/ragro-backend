package br.com.ragro.service;

import br.com.ragro.controller.request.CustomerUpdateRequest;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.mapper.AddressMapper;
import br.com.ragro.mapper.CustomerMapper;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.UserRepository;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private final UserService userService;
  private final UserRepository userRepository;
  private final AddressRepository addressRepository;
  private final CustomerRepository customerRepository;

  public CustomerService(
      UserService userService,
      UserRepository userRepository,
      AddressRepository addressRepository,
      CustomerRepository customerRepository) {
    this.userService = userService;
    this.userRepository = userRepository;
    this.addressRepository = addressRepository;
    this.customerRepository = customerRepository;
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
            .orElseGet(
                () -> {
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

  @Transactional(readOnly = true)
  public CustomerResponse getCustomerById(UUID id, Jwt jwt) {
    User requester = userService.getAuthenticatedUser(jwt);

    if (requester.getType() != TypeUser.ADMIN) {
      throw new UnauthorizedException("Access restricted to admins");
    }

    Customer customer =
        customerRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Customer not found"));

    return CustomerMapper.toResponse(customer.getUser());
  }
}
