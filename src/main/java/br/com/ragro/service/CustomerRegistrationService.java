package br.com.ragro.service;

import br.com.ragro.controller.request.AddressRequest;
import br.com.ragro.controller.request.CustomerRegistrationRequest;
import br.com.ragro.controller.response.CustomerRegistrationResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.mapper.AddressMapper;
import br.com.ragro.mapper.CustomerMapper;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.CustomerRepository;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.api.IdentityProviderService;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRegistrationService {

  private static final Logger log = LoggerFactory.getLogger(CustomerRegistrationService.class);

  private final UserRepository userRepository;
  private final CustomerRepository customerRepository;
  private final AddressRepository addressRepository;
  private final IdentityProviderService identityProviderService;

  public CustomerRegistrationService(
      UserRepository userRepository,
      CustomerRepository customerRepository,
      AddressRepository addressRepository,
      IdentityProviderService identityProviderService) {
    this.userRepository = userRepository;
    this.customerRepository = customerRepository;
    this.addressRepository = addressRepository;
    this.identityProviderService = identityProviderService;
  }

  @Transactional
  public CustomerRegistrationResponse register(CustomerRegistrationRequest request) {
    String normalizedEmail = normalizeEmail(request.getEmail());
    String normalizedPhone = normalizePhone(request.getPhone());
    String normalizedFiscalNumber = digitsOnly(request.getFiscalNumber());
    AddressRequest normalizedAddress = normalizeAddress(request.getAddress());

    validateUniqueness(normalizedEmail, normalizedFiscalNumber);

    String externalUserId =
        identityProviderService.registerCustomer(normalizedEmail, request.getPassword());

    User savedUser;
    Address savedAddress;
    try {
      User user = new User();
      user.setName(request.getName().trim());
      user.setEmail(normalizedEmail);
      user.setPhone(normalizedPhone);
      user.setType(TypeUser.CUSTOMER);
      user.setActive(true);
      user.setAuthSub(externalUserId);

      savedUser = userRepository.saveAndFlush(user);
      customerRepository.saveAndFlush(CustomerMapper.toEntity(savedUser, normalizedFiscalNumber));

      Address address = AddressMapper.toEntity(normalizedAddress, savedUser, true);
      savedAddress = addressRepository.save(address);
    } catch (Exception original) {
      try {
        identityProviderService.deleteUser(externalUserId);
      } catch (Exception compensation) {
        log.error("Keycloak compensation failed for user {}: {}", externalUserId, compensation.getMessage());
      }
      throw original;
    }

    return CustomerRegistrationResponse.builder()
        .id(savedUser.getId())
        .name(savedUser.getName())
        .email(savedUser.getEmail())
        .phone(savedUser.getPhone())
        .type(savedUser.getType().name().toLowerCase(Locale.ROOT))
        .active(savedUser.isActive())
        .fiscalNumber(normalizedFiscalNumber)
        .address(AddressMapper.toResponse(savedAddress))
        .createdAt(savedUser.getCreatedAt())
        .updatedAt(savedUser.getUpdatedAt())
        .build();
  }

  private void validateUniqueness(String email, String fiscalNumber) {
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException("E-mail already registered");
    }

    if (customerRepository.existsByFiscalNumber(fiscalNumber)) {
      throw new BusinessException("Fiscal number already registered");
    }
  }

  private AddressRequest normalizeAddress(AddressRequest address) {
    AddressRequest normalized = new AddressRequest();
    normalized.setStreet(address.getStreet().trim());
    normalized.setNumber(address.getNumber().trim());
    normalized.setComplement(trimToNull(address.getComplement()));
    normalized.setNeighborhood(trimToNull(address.getNeighborhood()));
    normalized.setCity(address.getCity().trim());
    normalized.setState(address.getState().trim().toUpperCase(Locale.ROOT));
    normalized.setZipCode(digitsOnly(address.getZipCode()));
    normalized.setLatitude(address.getLatitude());
    normalized.setLongitude(address.getLongitude());
    return normalized;
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizePhone(String phone) {
    return phone.trim();
  }

  private String digitsOnly(String value) {
    return value.replaceAll("\\D", "");
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }

    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
