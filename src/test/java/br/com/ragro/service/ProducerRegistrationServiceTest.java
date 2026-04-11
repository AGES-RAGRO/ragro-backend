package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.AddressRequest;
import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.response.ProducerRegistrationResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.FarmerAvailabilityRepository;
import br.com.ragro.repository.PaymentMethodRepository;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.api.IdentityProviderService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProducerRegistrationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ProducerRepository producerRepository;
    @Mock private IdentityProviderService identityProviderService;
    @Mock private AddressRepository addressRepository;
    @Mock private FarmerAvailabilityRepository availabilityRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;

    @InjectMocks private ProducerRegistrationService producerRegistrationService;

    private ProducerRegistrationRequest validRequest() {
        AddressRequest address = new AddressRequest();
        address.setStreet("Rua das Flores");
        address.setNumber("123");
        address.setCity("Porto Alegre");
        address.setState("RS");
        address.setZipCode("90010120");

        ProducerRegistrationRequest request = new ProducerRegistrationRequest();
        request.setName("João Silva");
        request.setPhone("51988888888");
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        request.setFiscalNumber("12345678901");
        request.setFiscalNumberType("CPF");
        request.setFarmName("Fazenda São João");
        request.setDescription("Produção orgânica");
        request.setAddress(address);
        return request;
    }

    private User buildSavedUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setName("João Silva");
        user.setEmail("joao@example.com");
        user.setPhone("51988888888");
        user.setType(TypeUser.FARMER);
        user.setActive(true);
        user.setAuthSub("auth-sub-" + id);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }

    private Producer buildSavedProducer(User user) {
        Producer producer = new Producer();
        producer.setId(user.getId());
        producer.setUser(user);
        producer.setFiscalNumber("12345678901");
        producer.setFiscalNumberType("CPF");
        producer.setFarmName("Fazenda São João");
        producer.setDescription("Produção orgânica");
        producer.setTotalReviews(0);
        producer.setAverageRating(BigDecimal.ZERO);
        producer.setTotalOrders(0);
        producer.setTotalSalesAmount(BigDecimal.ZERO);
        return producer;
    }

    @Test
    void register_shouldReturnResponse_whenRequestIsValid() {
        UUID id = UUID.randomUUID();
        User savedUser = buildSavedUser(id);
        Producer savedProducer = buildSavedProducer(savedUser);

        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);
        when(producerRepository.existsByFiscalNumber("12345678901")).thenReturn(false);
        when(identityProviderService.registerProducer(anyString(), anyString())).thenReturn("auth-sub-" + id);
        when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
        when(producerRepository.saveAndFlush(any())).thenReturn(savedProducer);

        ProducerRegistrationResponse response = producerRegistrationService.register(validRequest());

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getName()).isEqualTo("João Silva");
        assertThat(response.getEmail()).isEqualTo("joao@example.com");
        assertThat(response.getPhone()).isEqualTo("51988888888");
        assertThat(response.getType()).isEqualTo("farmer");
        assertThat(response.isActive()).isTrue();
        assertThat(response.getFiscalNumber()).isEqualTo("12345678901");
        assertThat(response.getFiscalNumberType()).isEqualTo("CPF");
        assertThat(response.getFarmName()).isEqualTo("Fazenda São João");
        assertThat(response.getTotalReviews()).isEqualTo(0);
        assertThat(response.getTotalOrders()).isEqualTo(0);
        assertThat(response.getAverageRating()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotalSalesAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void register_shouldNormalizeEmail_beforeSaving() {
        UUID id = UUID.randomUUID();
        User savedUser = buildSavedUser(id);
        Producer savedProducer = buildSavedProducer(savedUser);

        ProducerRegistrationRequest request = validRequest();
        request.setEmail("  JOAO@EXAMPLE.COM  ");

        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);
        when(producerRepository.existsByFiscalNumber(anyString())).thenReturn(false);
        when(identityProviderService.registerProducer(anyString(), anyString())).thenReturn("auth-sub");
        when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
        when(producerRepository.saveAndFlush(any())).thenReturn(savedProducer);

        producerRegistrationService.register(request);

        verify(identityProviderService).registerProducer("joao@example.com", "Senha@123");
    }

    @Test
    void register_shouldNormalizeFiscalNumber_removingNonDigits() {
        UUID id = UUID.randomUUID();
        User savedUser = buildSavedUser(id);
        Producer savedProducer = buildSavedProducer(savedUser);

        ProducerRegistrationRequest request = validRequest();
        request.setFiscalNumber("123.456.789-01");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(producerRepository.existsByFiscalNumber("12345678901")).thenReturn(false);
        when(identityProviderService.registerProducer(anyString(), anyString())).thenReturn("auth-sub");
        when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
        when(producerRepository.saveAndFlush(any())).thenReturn(savedProducer);

        ProducerRegistrationResponse response = producerRegistrationService.register(request);

        assertThat(response.getFiscalNumber()).isEqualTo("12345678901");
    }

    @Test
    void register_shouldThrowBusinessException_whenEmailAlreadyRegistered() {
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(true);

        assertThatThrownBy(() -> producerRegistrationService.register(validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("E-mail already registered");

        verify(identityProviderService, never()).registerProducer(anyString(), anyString());
        verify(userRepository, never()).saveAndFlush(any());
        verify(producerRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_shouldThrowBusinessException_whenFiscalNumberAlreadyRegistered() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(producerRepository.existsByFiscalNumber("12345678901")).thenReturn(true);

        assertThatThrownBy(() -> producerRegistrationService.register(validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Fiscal number already registered");

        verify(identityProviderService, never()).registerProducer(anyString(), anyString());
        verify(userRepository, never()).saveAndFlush(any());
        verify(producerRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_shouldDeleteKeycloakUser_whenSavingUserFails() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(producerRepository.existsByFiscalNumber(anyString())).thenReturn(false);
        when(identityProviderService.registerProducer(anyString(), anyString())).thenReturn("auth-sub-123");
        when(userRepository.saveAndFlush(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> producerRegistrationService.register(validRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");

        verify(identityProviderService).deleteUser("auth-sub-123");
    }

    @Test
    void register_shouldDeleteKeycloakUser_whenSavingProducerFails() {
        UUID id = UUID.randomUUID();
        User savedUser = buildSavedUser(id);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(producerRepository.existsByFiscalNumber(anyString())).thenReturn(false);
        when(identityProviderService.registerProducer(anyString(), anyString())).thenReturn("auth-sub-123");
        when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
        when(producerRepository.saveAndFlush(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> producerRegistrationService.register(validRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");

        verify(identityProviderService).deleteUser("auth-sub-123");
    }

    @Test
    void register_shouldSaveUserWithCorrectType() {
        UUID id = UUID.randomUUID();
        User savedUser = buildSavedUser(id);
        Producer savedProducer = buildSavedProducer(savedUser);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(producerRepository.existsByFiscalNumber(anyString())).thenReturn(false);
        when(identityProviderService.registerProducer(anyString(), anyString())).thenReturn("auth-sub");
        when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
        when(producerRepository.saveAndFlush(any())).thenReturn(savedProducer);

        ProducerRegistrationResponse response = producerRegistrationService.register(validRequest());

        assertThat(response.getType()).isEqualTo("farmer");
    }
}