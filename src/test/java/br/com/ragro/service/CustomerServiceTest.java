package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.AddressRequest;
import br.com.ragro.controller.request.CustomerUpdateRequest;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.domain.Customer;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  @Mock private UserService userService;
  @Mock private UserRepository userRepository;
  @Mock private AddressRepository addressRepository;
  @Mock private CustomerRepository customerRepository;
  @Mock private Jwt jwt;

  @InjectMocks private CustomerService customerService;

  @Test
  void getMyCustomer_shouldReturnCustomerResponse_whenUserIsCustomer() {
    User user = buildUser(TypeUser.CUSTOMER);
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

    CustomerResponse response = customerService.getMyCustomer(jwt);

    assertThat(response.getId()).isEqualTo(user.getId());
    assertThat(response.getName()).isEqualTo(user.getName());
    assertThat(response.getEmail()).isEqualTo(user.getEmail());
    assertThat(response.getAddresses()).isEmpty();
  }

  @Test
  void getMyCustomer_shouldThrowUnauthorizedException_whenUserIsNotCustomer() {
    User user = buildUser(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

    assertThatThrownBy(() -> customerService.getMyCustomer(jwt))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Access restricted to customers");
  }

  @Test
  void getMyCustomer_shouldThrowUnauthorizedException_whenUserIsAdmin() {
    User user = buildUser(TypeUser.ADMIN);
    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

    assertThatThrownBy(() -> customerService.getMyCustomer(jwt))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void updateMe_shouldReturnUpdatedCustomerResponse_whenUserIsCustomer() {
    User user = buildUser(TypeUser.CUSTOMER);
    User updatedUser = buildUser(TypeUser.CUSTOMER);
    updatedUser.setId(user.getId());
    updatedUser.setName("Novo Nome");
    updatedUser.setPhone("51988887777");

    when(userService.getAuthenticatedUser(jwt)).thenReturn(user);
    when(addressRepository.findByUserIdAndIsPrimaryTrue(user.getId()))
        .thenReturn(Optional.empty());
    when(userRepository.findById(user.getId())).thenReturn(Optional.of(updatedUser));

    CustomerUpdateRequest request = buildUpdateRequest("Novo Nome", "51988887777");
    CustomerResponse response = customerService.updateMyCustomer(jwt, request);

    assertThat(response.getName()).isEqualTo("Novo Nome");
    assertThat(response.getPhone()).isEqualTo("51988887777");
  }

  @Test
  void updateMe_shouldThrowUnauthorizedException_whenUserIsNotCustomer() {
    User farmer = buildUser(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmer);

    CustomerUpdateRequest request = buildUpdateRequest("Nome Qualquer", "51988887777");

    assertThatThrownBy(() -> customerService.updateMyCustomer(jwt, request))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Access restricted to customers");
  }

  @Test
  void getCustomerById_shouldReturnCustomerResponse_whenCallerIsAdmin() {
    User admin = buildUser(TypeUser.ADMIN);
    User customerUser = buildUser(TypeUser.CUSTOMER);
    Customer customer = buildCustomer(customerUser);
    when(userService.getAuthenticatedUser(jwt)).thenReturn(admin);
    when(customerRepository.findById(Objects.requireNonNull(customerUser.getId())))
        .thenReturn(Optional.of(customer));

    CustomerResponse response = customerService.getCustomerById(customerUser.getId(), jwt);

    assertThat(response.getId()).isEqualTo(customerUser.getId());
    assertThat(response.getName()).isEqualTo(customerUser.getName());
    assertThat(response.getEmail()).isEqualTo(customerUser.getEmail());
    assertThat(response.getAddresses()).isEmpty();
  }

  @Test
  void getCustomerById_shouldThrowNotFoundException_whenCustomerDoesNotExist() {
    User admin = buildUser(TypeUser.ADMIN);
    UUID unknownId = UUID.randomUUID();
    when(userService.getAuthenticatedUser(jwt)).thenReturn(admin);
    when(customerRepository.findById(Objects.requireNonNull(unknownId)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.getCustomerById(unknownId, jwt))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Customer not found");
  }

  @Test
  void getCustomerById_shouldThrowUnauthorizedException_whenCallerIsCustomer() {
    User customer = buildUser(TypeUser.CUSTOMER);
    when(userService.getAuthenticatedUser(jwt)).thenReturn(customer);

    assertThatThrownBy(() -> customerService.getCustomerById(customer.getId(), jwt))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Access restricted to admins");
  }

  @Test
  void getCustomerById_shouldThrowUnauthorizedException_whenCallerIsFarmer() {
    User farmer = buildUser(TypeUser.FARMER);
    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmer);

    assertThatThrownBy(() -> customerService.getCustomerById(farmer.getId(), jwt))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Access restricted to admins");
  }

  private CustomerUpdateRequest buildUpdateRequest(String name, String phone) {
    AddressRequest address = new AddressRequest();
    address.setStreet("Rua das Flores");
    address.setNumber("123");
    address.setCity("Porto Alegre");
    address.setState("RS");
    address.setZipCode("90010120");

    CustomerUpdateRequest request = new CustomerUpdateRequest();
    request.setName(name);
    request.setPhone(phone);
    request.setAddress(address);
    return request;
  }

  private User buildUser(TypeUser type) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Maria Silva");
    user.setEmail("maria@example.com");
    user.setPhone("51999999999");
    user.setType(type);
    user.setActive(true);
    user.setAuthSub("auth-sub-123");
    user.setCreatedAt(OffsetDateTime.now().minusDays(1));
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }

  private Customer buildCustomer(User user) {
    Customer customer = new Customer();
    customer.setUser(user);
    customer.setFiscalNumber("12345678901");
    return customer;
  }
}
