package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.AddressRequest;
import br.com.ragro.controller.request.ConsumerRegisterRequest;
import br.com.ragro.controller.response.ConsumerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ConsumerServiceTest {

    @Mock private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private CognitoService cognitoService;
    @Mock private Jwt jwt;

    @InjectMocks private ConsumerService consumerService;

    // ── registerConsumer ──────────────────────────────────────────────────────

    @Test
    void registerConsumer_shouldPersistUserWithCustomerType() {
        ConsumerRegisterRequest request = buildRequest();
        String cognitoSub = "cognito-sub-abc";

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(cognitoService.registerUser(request.getName(), request.getEmail()))
                .thenReturn(cognitoSub);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        consumerService.registerConsumer(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User saved = userCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(TypeUser.CUSTOMER);
        assertThat(saved.getEmail()).isEqualTo(request.getEmail());
        assertThat(saved.getName()).isEqualTo(request.getName());
        assertThat(saved.getPhone()).isEqualTo(request.getPhone());
        assertThat(saved.getCognitoSub()).isEqualTo(cognitoSub);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void registerConsumer_shouldPersistAddressAsPrimary() {
        ConsumerRegisterRequest request = buildRequest();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(cognitoService.registerUser(any(), any())).thenReturn("sub-123");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        consumerService.registerConsumer(request);

        ArgumentCaptor<Address> addressCaptor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(addressCaptor.capture());

        Address saved = addressCaptor.getValue();
        assertThat(saved.isPrimary()).isTrue();
        assertThat(saved.getStreet()).isEqualTo(request.getAddress().getStreet());
        assertThat(saved.getCity()).isEqualTo(request.getAddress().getCity());
        assertThat(saved.getState()).isEqualTo(request.getAddress().getState());
        assertThat(saved.getZipCode()).isEqualTo(request.getAddress().getZipCode());
    }

    @Test
    void registerConsumer_shouldCallCognitoAddToGroupWithCorrectSub() {
        ConsumerRegisterRequest request = buildRequest();
        String cognitoSub = "unique-sub-xyz";

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(cognitoService.registerUser(any(), any())).thenReturn(cognitoSub);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        consumerService.registerConsumer(request);

        verify(cognitoService).addToConsumerGroup(cognitoSub);
    }

    @Test
    void registerConsumer_shouldReturnConsumerResponse() {
        ConsumerRegisterRequest request = buildRequest();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(cognitoService.registerUser(any(), any())).thenReturn("sub-123");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsumerResponse response = consumerService.registerConsumer(request);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo(request.getEmail());
        assertThat(response.getName()).isEqualTo(request.getName());
    }

    @Test
    void registerConsumer_shouldThrowBusinessException_whenEmailAlreadyExists() {
        ConsumerRegisterRequest request = buildRequest();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> consumerService.registerConsumer(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("E-mail já cadastrado");

        verify(cognitoService, never()).registerUser(any(), any());
        verify(userRepository, never()).save(any());
        verify(addressRepository, never()).save(any());
    }

    // ── getMyConsumer ─────────────────────────────────────────────────────────

    @Test
    void getMyConsumer_shouldReturnConsumerResponse_whenUserIsCustomer() {
        User user = buildUser(TypeUser.CUSTOMER);
        when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

        ConsumerResponse response = consumerService.getMyConsumer(jwt);

        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getName()).isEqualTo(user.getName());
        assertThat(response.getEmail()).isEqualTo(user.getEmail());
        assertThat(response.getAddresses()).isEmpty();
    }

    @Test
    void getMyConsumer_shouldThrowUnauthorizedException_whenUserIsNotCustomer() {
        User user = buildUser(TypeUser.FARMER);
        when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

        assertThatThrownBy(() -> consumerService.getMyConsumer(jwt))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Acesso restrito a consumidores");
    }

    @Test
    void getMyConsumer_shouldThrowUnauthorizedException_whenUserIsAdmin() {
        User user = buildUser(TypeUser.ADMIN);
        when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

        assertThatThrownBy(() -> consumerService.getMyConsumer(jwt))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ConsumerRegisterRequest buildRequest() {
        AddressRequest address = new AddressRequest();
        address.setStreet("Rua das Flores");
        address.setNumber("42");
        address.setComplement("Apto 3");
        address.setNeighborhood("Centro");
        address.setCity("Porto Alegre");
        address.setState("RS");
        address.setZipCode("90010000");
        address.setLatitude(new BigDecimal("-30.0277"));
        address.setLongitude(new BigDecimal("-51.2287"));

        ConsumerRegisterRequest request = new ConsumerRegisterRequest();
        request.setName("Maria Silva");
        request.setEmail("maria@example.com");
        request.setPhone("51999999999");
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
        user.setCognitoSub("cognito-sub-123");
        user.setCreatedAt(OffsetDateTime.now().minusDays(1));
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }
}
