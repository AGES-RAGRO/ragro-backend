package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.AddressRequest;
import br.com.ragro.controller.request.CustomerRegistrationRequest;
import br.com.ragro.controller.response.CustomerRegistrationResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.ConflictException;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.api.IdentityProviderService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerRegistrationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private CustomerRepository customerRepository;
  @Mock private AddressRepository addressRepository;
  @Mock private IdentityProviderService identityProviderService;

  @InjectMocks private CustomerRegistrationService customerRegistrationService;

  // ─── validRequest ─────────────────────────────────────────────────────────

  private CustomerRegistrationRequest validRequest() {
    AddressRequest address = new AddressRequest();
    address.setStreet("Rua das Flores");
    address.setNumber("123");
    address.setCity("Porto Alegre");
    address.setState("RS");
    address.setZipCode("90010120");

    CustomerRegistrationRequest request = new CustomerRegistrationRequest();
    request.setName("Maria Silva");
    request.setEmail("maria@example.com");
    request.setPhone("51987654321");
    request.setPassword("Senha@123");
    request.setFiscalNumber("52998224725");
    request.setAddress(address);
    return request;
  }

  private User buildSavedUser(UUID id) {
    User user = new User();
    user.setId(id);
    user.setName("Maria Silva");
    user.setEmail("maria@example.com");
    user.setPhone("51987654321");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(true);
    user.setAuthSub("auth-sub-" + id);
    user.setCreatedAt(OffsetDateTime.now());
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }

  private Address buildSavedAddress(User user) {
    Address address = new Address();
    address.setId(UUID.randomUUID());
    address.setStreet("Rua das Flores");
    address.setNumber("123");
    address.setCity("Porto Alegre");
    address.setState("RS");
    address.setZipCode("90010120");
    address.setUser(user);
    address.setPrimary(true);
    return address;
  }

  // ─── happy path ──────────────────────────────────────────────────────────

  @Test
  void register_shouldReturnResponse_whenRequestIsValid() {
    UUID id = UUID.randomUUID();
    User savedUser = buildSavedUser(id);
    Address savedAddress = buildSavedAddress(savedUser);

    when(userRepository.existsByEmail("maria@example.com")).thenReturn(false);
    when(customerRepository.existsByFiscalNumber("52998224725")).thenReturn(false);
    when(identityProviderService.registerCustomer(anyString(), anyString())).thenReturn("auth-sub-" + id);
    when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
    when(customerRepository.saveAndFlush(any())).thenReturn(null);
    when(addressRepository.save(any())).thenReturn(savedAddress);

    CustomerRegistrationResponse response = customerRegistrationService.register(validRequest());

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(id);
    assertThat(response.getName()).isEqualTo("Maria Silva");
    assertThat(response.getEmail()).isEqualTo("maria@example.com");
    assertThat(response.getType()).isEqualTo("customer");
    assertThat(response.isActive()).isTrue();
    assertThat(response.getFiscalNumber()).isEqualTo("52998224725");
  }

  @Test
  void register_shouldNormalizeEmail_beforeSaving() {
    UUID id = UUID.randomUUID();
    User savedUser = buildSavedUser(id);

    CustomerRegistrationRequest request = validRequest();
    request.setEmail("  MARIA@EXAMPLE.COM  ");

    when(userRepository.existsByEmail("maria@example.com")).thenReturn(false);
    when(customerRepository.existsByFiscalNumber(anyString())).thenReturn(false);
    when(identityProviderService.registerCustomer("maria@example.com", "Senha@123")).thenReturn("auth-sub");
    when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
    when(customerRepository.saveAndFlush(any())).thenReturn(null);
    when(addressRepository.save(any())).thenReturn(buildSavedAddress(savedUser));

    customerRegistrationService.register(request);

    verify(identityProviderService).registerCustomer("maria@example.com", "Senha@123");
  }

  @Test
  void register_shouldNormalizeFiscalNumber_removingNonDigits() {
    UUID id = UUID.randomUUID();
    User savedUser = buildSavedUser(id);

    CustomerRegistrationRequest request = validRequest();
    request.setFiscalNumber("529.982.247-25");

    when(userRepository.existsByEmail(anyString())).thenReturn(false);
    when(customerRepository.existsByFiscalNumber("52998224725")).thenReturn(false);
    when(identityProviderService.registerCustomer(anyString(), anyString())).thenReturn("auth-sub");
    when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
    when(customerRepository.saveAndFlush(any())).thenReturn(null);
    when(addressRepository.save(any())).thenReturn(buildSavedAddress(savedUser));

    CustomerRegistrationResponse response = customerRegistrationService.register(request);

    assertThat(response.getFiscalNumber()).isEqualTo("52998224725");
  }

  // ─── uniqueness validation ────────────────────────────────────────────────

  @Test
  void register_shouldThrowConflictException_whenEmailAlreadyRegistered() {
    when(userRepository.existsByEmail("maria@example.com")).thenReturn(true);

    assertThatThrownBy(() -> customerRegistrationService.register(validRequest()))
        .isInstanceOf(ConflictException.class)
        .hasMessage("E-mail already registered");

    verify(identityProviderService, never()).registerCustomer(anyString(), anyString());
    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  void register_shouldThrowConflictException_whenFiscalNumberAlreadyRegistered() {
    when(userRepository.existsByEmail(anyString())).thenReturn(false);
    when(customerRepository.existsByFiscalNumber("52998224725")).thenReturn(true);

    assertThatThrownBy(() -> customerRegistrationService.register(validRequest()))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Fiscal number already registered");

    verify(identityProviderService, never()).registerCustomer(anyString(), anyString());
    verify(userRepository, never()).saveAndFlush(any());
  }

  // ─── compensatory transaction ─────────────────────────────────────────────

  @Test
  void register_shouldDeleteKeycloakUser_whenSavingUserFails() {
    when(userRepository.existsByEmail(anyString())).thenReturn(false);
    when(customerRepository.existsByFiscalNumber(anyString())).thenReturn(false);
    when(identityProviderService.registerCustomer(anyString(), anyString())).thenReturn("auth-sub-123");
    when(userRepository.saveAndFlush(any())).thenThrow(new RuntimeException("DB error"));

    assertThatThrownBy(() -> customerRegistrationService.register(validRequest()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("DB error");

    verify(identityProviderService).deleteUser("auth-sub-123");
  }

  @Test
  void register_shouldDeleteKeycloakUser_whenSavingCustomerFails() {
    UUID id = UUID.randomUUID();
    User savedUser = buildSavedUser(id);

    when(userRepository.existsByEmail(anyString())).thenReturn(false);
    when(customerRepository.existsByFiscalNumber(anyString())).thenReturn(false);
    when(identityProviderService.registerCustomer(anyString(), anyString())).thenReturn("auth-sub-123");
    when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
    when(customerRepository.saveAndFlush(any())).thenThrow(new RuntimeException("DB error"));

    assertThatThrownBy(() -> customerRegistrationService.register(validRequest()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("DB error");

    verify(identityProviderService).deleteUser("auth-sub-123");
  }

  @Test
  void register_shouldDeleteKeycloakUser_whenSavingAddressFails() {
    UUID id = UUID.randomUUID();
    User savedUser = buildSavedUser(id);

    when(userRepository.existsByEmail(anyString())).thenReturn(false);
    when(customerRepository.existsByFiscalNumber(anyString())).thenReturn(false);
    when(identityProviderService.registerCustomer(anyString(), anyString())).thenReturn("auth-sub-123");
    when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
    when(customerRepository.saveAndFlush(any())).thenReturn(null);
    when(addressRepository.save(any())).thenThrow(new RuntimeException("DB error"));

    assertThatThrownBy(() -> customerRegistrationService.register(validRequest()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("DB error");

    verify(identityProviderService).deleteUser("auth-sub-123");
  }

  @Test
  void register_shouldPropagateOriginalException_whenKeycloakCompensationFails() {
    RuntimeException originalException = new RuntimeException("DB error");
    RuntimeException compensationException = new RuntimeException("Keycloak unavailable");

    when(userRepository.existsByEmail(anyString())).thenReturn(false);
    when(customerRepository.existsByFiscalNumber(anyString())).thenReturn(false);
    when(identityProviderService.registerCustomer(anyString(), anyString())).thenReturn("auth-sub-123");
    when(userRepository.saveAndFlush(any())).thenThrow(originalException);
    doThrow(compensationException).when(identityProviderService).deleteUser("auth-sub-123");

    assertThatThrownBy(() -> customerRegistrationService.register(validRequest()))
        .isSameAs(originalException);

    verify(identityProviderService).deleteUser("auth-sub-123");
  }
}
