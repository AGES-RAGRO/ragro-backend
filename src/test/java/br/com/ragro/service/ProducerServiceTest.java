package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.PaymentMethodRequest;
import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.PaymentMethod;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.ProducerProfile;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.AddressRepository;
import br.com.ragro.repository.FarmerAvailabilityRepository;
import br.com.ragro.repository.PaymentMethodRepository;
import br.com.ragro.repository.ProducerProfileRepository;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ProducerServiceTest {

  @Mock
  private UserRepository userRepository;
  @Mock
  private ProducerRepository producerRepository;
  @Mock
  private ProducerProfileRepository producerProfileRepository;
  @Mock
  private AddressRepository addressRepository;
  @Mock
  private FarmerAvailabilityRepository farmerAvailabilityRepository;
  @Mock
  private PaymentMethodRepository paymentMethodRepository;
  @Mock
  private UserService userService;

  @InjectMocks
  private ProducerService producerService;

  // ─── getAllProducers ─────────────────────────────────────────────────────────

  @Test
  void getAllProducers_shouldReturnAllFarmers() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 10);
    Page<Producer> page = new PageImpl<>(
        List.of(
            buildProducerEntity(id1, buildProducer(id1)),
            buildProducerEntity(id2, buildProducer(id2))),
        pageable,
        2);
    when(producerRepository.findAllUsersSortedByRating(pageable)).thenReturn(page);

    Page<ProducerResponse> response = producerService.getAllProducers(pageable);

    assertThat(response.getContent()).hasSize(2);
    assertThat(response.getContent())
        .extracting(ProducerResponse::getId)
        .containsExactlyInAnyOrder(id1, id2);
    assertThat(response.getTotalElements()).isEqualTo(2);
  }

  @Test
  void getAllProducers_shouldReturnEmptyPage_whenNoFarmersExist() {
    Pageable pageable = PageRequest.of(0, 10);
    when(producerRepository.findAllUsersSortedByRating(pageable)).thenReturn(Page.empty(pageable));

    Page<ProducerResponse> response = producerService.getAllProducers(pageable);

    assertThat(response.getContent()).isEmpty();
    assertThat(response.getTotalElements()).isZero();
  }

  @Test
  void getAllProducers_shouldReturnBothActiveAndInactiveProducers() {
    UUID activeId = UUID.randomUUID();
    UUID inactiveId = UUID.randomUUID();
    User activeProducer = buildProducer(activeId);
    User inactiveProducer = buildProducer(inactiveId);
    inactiveProducer.setActive(false);
    Pageable pageable = PageRequest.of(0, 10);
    Page<Producer> page = new PageImpl<>(
        List.of(
            buildProducerEntity(activeId, activeProducer),
            buildProducerEntity(inactiveId, inactiveProducer)),
        pageable,
        2);
    when(producerRepository.findAllUsersSortedByRating(pageable)).thenReturn(page);

    Page<ProducerResponse> response = producerService.getAllProducers(pageable);

    assertThat(response.getContent()).hasSize(2);
    assertThat(response.getContent())
        .extracting(ProducerResponse::isActive)
        .containsExactlyInAnyOrder(true, false);
  }

  @Test
  void getProducerById_shouldReturnProducerResponse_whenProducerExists() {
    UUID producerId = UUID.randomUUID();
    User user = buildProducer(producerId);
    Producer producer = buildProducerEntity(producerId, user);
    when(producerRepository.findDetailedById(producerId)).thenReturn(Optional.of(producer));

    ProducerResponse response = producerService.getProducerById(producerId);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(producerId);
    assertThat(response.getName()).isEqualTo("João Farmer");
    assertThat(response.getEmail()).isEqualTo(user.getEmail());
    assertThat(response.getPhone()).isEqualTo(user.getPhone());
    assertThat(response.isActive()).isTrue();
    assertThat(response.getCreatedAt()).isNotNull();
    assertThat(response.getUpdatedAt()).isNotNull();
  }

  @Test
  void getProducerById_shouldThrowNotFoundException_whenProducerNotFound() {
    UUID producerId = UUID.randomUUID();
    when(producerRepository.findDetailedById(producerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> producerService.getProducerById(producerId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  @Test
  void getProducerById_shouldThrowNotFoundException_whenUserIsNotFarmer() {
    UUID customerId = UUID.randomUUID();
    when(producerRepository.findDetailedById(customerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> producerService.getProducerById(customerId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  @Test
  void getProducerById_shouldThrowNotFoundException_whenUserIsAdmin() {
    UUID adminId = UUID.randomUUID();
    when(producerRepository.findDetailedById(adminId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> producerService.getProducerById(adminId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  // ─── activateProducer ───────────────────────────────────────────────────────

  @Test
  void activateProducer_shouldActivateAndReturnResponse_whenProducerExists() {
    UUID producerId = UUID.randomUUID();
    User producer = buildProducer(producerId);
    producer.setActive(false);
    when(userRepository.findById(producerId)).thenReturn(Optional.of(producer));
    when(userRepository.saveAndFlush(producer)).thenReturn(producer);

    ProducerResponse response = producerService.activateProducer(producerId);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(producerId);
    assertThat(response.isActive()).isTrue();
    verify(userRepository).saveAndFlush(producer);
  }

  @Test
  void activateProducer_shouldThrowNotFoundException_whenProducerNotFound() {
    UUID producerId = UUID.randomUUID();
    when(userRepository.findById(producerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> producerService.activateProducer(producerId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  @Test
  void activateProducer_shouldThrowNotFoundException_whenUserIsNotFarmer() {
    UUID customerId = UUID.randomUUID();
    User customer = buildUser(customerId, TypeUser.CUSTOMER, "Maria Customer");
    when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

    assertThatThrownBy(() -> producerService.activateProducer(customerId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  @Test
  void activateProducer_shouldThrowNotFoundException_whenUserIsAdmin() {
    UUID adminId = UUID.randomUUID();
    User admin = buildUser(adminId, TypeUser.ADMIN, "Admin User");
    when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> producerService.activateProducer(adminId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  @Test
  void deactivateProducer_shouldThrowNotFoundException_whenProducerNotFound() {
    UUID producerId = UUID.randomUUID();
    when(userRepository.findById(producerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> producerService.deactivateProducer(producerId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  @Test
  void deactivateProducer_shouldThrowNotFoundException_whenUserIsNotFarmer() {
    UUID customerId = UUID.randomUUID();
    User customer = buildUser(customerId, TypeUser.CUSTOMER, "Maria Customer");
    when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

    assertThatThrownBy(() -> producerService.deactivateProducer(customerId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  @Test
  void deactivateProducer_shouldThrowNotFoundException_whenUserIsAdmin() {
    UUID adminId = UUID.randomUUID();
    User admin = buildUser(adminId, TypeUser.ADMIN, "Admin User");
    when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> producerService.deactivateProducer(adminId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  // ─── updateProducerProfile — FARMER ────────────────────────────────────────

  @Test
  void updateProducerProfile_shouldUpdateAndReturn_whenFarmerUpdatesOwnProfile() {
    UUID producerId = UUID.randomUUID();
    User farmer = buildProducer(producerId);
    Producer producer = buildProducerEntity(producerId, farmer);
    ProducerProfile profile = new ProducerProfile();
    profile.setUser(farmer);

    Jwt jwt = buildJwt(farmer.getAuthSub(), farmer.getEmail());

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Nome Atualizado");
    request.setFarmName("Fazenda Nova");
    request.setStory("Minha história");

    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmer);
    when(producerRepository.findDetailedById(producerId)).thenReturn(Optional.of(producer));
    when(userRepository.save(farmer)).thenReturn(farmer);
    when(producerRepository.save(producer)).thenReturn(producer);
    when(producerProfileRepository.findById(producerId)).thenReturn(Optional.of(profile));
    when(producerProfileRepository.save(profile)).thenReturn(profile);
    when(addressRepository.findByUserIdAndIsPrimaryTrue(producerId)).thenReturn(Optional.empty());
    when(paymentMethodRepository.findByFarmerIdAndActiveTrue(producerId)).thenReturn(List.of());

    ProducerGetResponse response = producerService.updateProducerProfile(producerId, jwt, request);

    assertThat(response).isNotNull();
    assertThat(farmer.getName()).isEqualTo("Nome Atualizado");
    assertThat(producer.getFarmName()).isEqualTo("Fazenda Nova");
    assertThat(profile.getStory()).isEqualTo("Minha história");
    verify(userRepository).save(farmer);
    verify(producerRepository).save(producer);
    verify(producerProfileRepository).save(profile);
  }

  @Test
  void updateProducerProfile_shouldThrowForbiddenException_whenFarmerTriesUpdateAnotherProfile() {
    UUID ownId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    User farmer = buildProducer(ownId);
    Jwt jwt = buildJwt(farmer.getAuthSub(), farmer.getEmail());

    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmer);

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Hacker");

    assertThatThrownBy(() -> producerService.updateProducerProfile(otherId, jwt, request))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Você não tem permissão para alterar este perfil");
  }

  @Test
  void updateProducerProfile_shouldThrowForbiddenException_whenUserIsNotFarmerOrAdmin() {
    UUID producerId = UUID.randomUUID();
    User customer = buildUser(producerId, TypeUser.CUSTOMER, "Maria Customer");
    Jwt jwt = buildJwt(customer.getAuthSub(), customer.getEmail());

    when(userService.getAuthenticatedUser(jwt)).thenReturn(customer);

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Hacker");

    assertThatThrownBy(() -> producerService.updateProducerProfile(producerId, jwt, request))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Acesso restrito a produtores e administradores");
  }

  @Test
  void updateProducerProfile_shouldCreateProducerProfile_whenItDoesNotExistYet() {
    UUID producerId = UUID.randomUUID();
    User farmer = buildProducer(producerId);
    Producer producer = buildProducerEntity(producerId, farmer);

    Jwt jwt = buildJwt(farmer.getAuthSub(), farmer.getEmail());

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setStory("Nova história");

    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmer);
    when(producerRepository.findDetailedById(producerId)).thenReturn(Optional.of(producer));
    when(userRepository.save(farmer)).thenReturn(farmer);
    when(producerRepository.save(producer)).thenReturn(producer);
    when(producerProfileRepository.findById(producerId)).thenReturn(Optional.empty());
    when(producerProfileRepository.save(any(ProducerProfile.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(addressRepository.findByUserIdAndIsPrimaryTrue(producerId)).thenReturn(Optional.empty());
    when(paymentMethodRepository.findByFarmerIdAndActiveTrue(producerId)).thenReturn(List.of());

    ProducerGetResponse response = producerService.updateProducerProfile(producerId, jwt, request);

    assertThat(response).isNotNull();
    verify(producerProfileRepository).save(any(ProducerProfile.class));
  }

  // ─── updateProducerProfile — ADMIN ─────────────────────────────────────────

  @Test
  void updateProducerProfile_shouldAllowAdmin_toUpdateAnyProducerProfile() {
    UUID farmerId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    User admin = buildUser(adminId, TypeUser.ADMIN, "Admin User");
    User farmer = buildProducer(farmerId);
    Producer producer = buildProducerEntity(farmerId, farmer);
    ProducerProfile profile = new ProducerProfile();
    profile.setUser(farmer);

    Jwt jwt = buildJwt(admin.getAuthSub(), admin.getEmail());

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setFarmName("Fazenda Editada pelo Admin");

    when(userService.getAuthenticatedUser(jwt)).thenReturn(admin);
    when(producerRepository.findDetailedById(farmerId)).thenReturn(Optional.of(producer));
    when(userRepository.save(farmer)).thenReturn(farmer);
    when(producerRepository.save(producer)).thenReturn(producer);
    when(producerProfileRepository.findById(farmerId)).thenReturn(Optional.of(profile));
    when(producerProfileRepository.save(profile)).thenReturn(profile);
    when(addressRepository.findByUserIdAndIsPrimaryTrue(farmerId)).thenReturn(Optional.empty());
    when(paymentMethodRepository.findByFarmerIdAndActiveTrue(farmerId)).thenReturn(List.of());

    ProducerGetResponse response = producerService.updateProducerProfile(farmerId, jwt, request);

    assertThat(response).isNotNull();
    assertThat(producer.getFarmName()).isEqualTo("Fazenda Editada pelo Admin");
  }

  // ─── updateProducerProfile — PaymentMethod ──────────────────────────────────

  @Test
  void updateProducerProfile_shouldUpsertPixPaymentMethod() {
    UUID producerId = UUID.randomUUID();
    User farmer = buildProducer(producerId);
    Producer producer = buildProducerEntity(producerId, farmer);
    ProducerProfile profile = new ProducerProfile();
    profile.setUser(farmer);

    Jwt jwt = buildJwt(farmer.getAuthSub(), farmer.getEmail());

    PaymentMethodRequest pmRequest = new PaymentMethodRequest();
    pmRequest.setType("pix");
    pmRequest.setPixKeyType("email");
    pmRequest.setPixKey("joao@email.com");

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setPaymentMethods(List.of(pmRequest));

    PaymentMethod savedPm = new PaymentMethod();
    savedPm.setId(UUID.randomUUID());
    savedPm.setFarmer(producer);
    savedPm.setType("pix");
    savedPm.setPixKey("joao@email.com");

    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmer);
    when(producerRepository.findDetailedById(producerId)).thenReturn(Optional.of(producer));
    when(userRepository.save(farmer)).thenReturn(farmer);
    when(producerRepository.save(producer)).thenReturn(producer);
    when(producerProfileRepository.findById(producerId)).thenReturn(Optional.of(profile));
    when(producerProfileRepository.save(profile)).thenReturn(profile);
    when(addressRepository.findByUserIdAndIsPrimaryTrue(producerId)).thenReturn(Optional.empty());
    when(paymentMethodRepository.findByFarmerIdAndTypeAndActiveTrue(producerId, "pix"))
        .thenReturn(Optional.empty());
    when(paymentMethodRepository.save(any(PaymentMethod.class))).thenReturn(savedPm);
    when(paymentMethodRepository.findByFarmerIdAndActiveTrue(producerId))
        .thenReturn(List.of(savedPm));

    ProducerGetResponse response = producerService.updateProducerProfile(producerId, jwt, request);

    assertThat(response).isNotNull();
    assertThat(response.getPaymentMethods()).hasSize(1);
    assertThat(response.getPaymentMethods().get(0).getPixKey()).isEqualTo("joao@email.com");
    verify(paymentMethodRepository).save(any(PaymentMethod.class));
  }

  @Test
  void updateProducerProfile_shouldNotSavePaymentMethod_whenNotProvided() {
    UUID producerId = UUID.randomUUID();
    User farmer = buildProducer(producerId);
    Producer producer = buildProducerEntity(producerId, farmer);
    ProducerProfile profile = new ProducerProfile();

    Jwt jwt = buildJwt(farmer.getAuthSub(), farmer.getEmail());

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Só nome");
    // paymentMethod == null

    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmer);
    when(producerRepository.findDetailedById(producerId)).thenReturn(Optional.of(producer));
    when(userRepository.save(farmer)).thenReturn(farmer);
    when(producerRepository.save(producer)).thenReturn(producer);
    when(producerProfileRepository.findById(producerId)).thenReturn(Optional.of(profile));
    when(producerProfileRepository.save(profile)).thenReturn(profile);
    when(addressRepository.findByUserIdAndIsPrimaryTrue(producerId)).thenReturn(Optional.empty());
    when(paymentMethodRepository.findByFarmerIdAndActiveTrue(producerId)).thenReturn(List.of());

    producerService.updateProducerProfile(producerId, jwt, request);

    verify(paymentMethodRepository, never()).save(any(PaymentMethod.class));
  }


  @Test
  void updateProducerProfile_shouldUpsertBothPaymentMethods_whenListHasTwoItems() {
    UUID producerId = UUID.randomUUID();
    User farmer = buildProducer(producerId);
    Producer producer = buildProducerEntity(producerId, farmer);
    ProducerProfile profile = new ProducerProfile();

    Jwt jwt = buildJwt(farmer.getAuthSub(), farmer.getEmail());

    PaymentMethodRequest pix = new PaymentMethodRequest();
    pix.setType("pix");
    pix.setPixKeyType("email");
    pix.setPixKey("joao@email.com");

    PaymentMethodRequest bank = new PaymentMethodRequest();
    bank.setType("bank_account");
    bank.setBankName("Banco do Brasil");
    bank.setAgency("0001");
    bank.setAccountNumber("123456-0");
    bank.setHolderName("João");

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setPaymentMethods(List.of(pix, bank));

    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmer);
    when(producerRepository.findDetailedById(producerId)).thenReturn(Optional.of(producer));
    when(userRepository.save(farmer)).thenReturn(farmer);
    when(producerRepository.save(producer)).thenReturn(producer);
    when(producerProfileRepository.findById(producerId)).thenReturn(Optional.of(profile));
    when(producerProfileRepository.save(profile)).thenReturn(profile);
    when(addressRepository.findByUserIdAndIsPrimaryTrue(producerId)).thenReturn(Optional.empty());
    when(paymentMethodRepository.findByFarmerIdAndTypeAndActiveTrue(eq(producerId), anyString()))
        .thenReturn(Optional.empty());
    when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(i -> i.getArgument(0));
    when(paymentMethodRepository.findByFarmerIdAndActiveTrue(producerId)).thenReturn(List.of());

    producerService.updateProducerProfile(producerId, jwt, request);

    verify(paymentMethodRepository, times(2)).save(any(PaymentMethod.class));
  }

  @Test
  void updateProducerProfile_shouldThrowBusinessException_whenDuplicatePaymentMethodTypes() {
    UUID producerId = UUID.randomUUID();
    User farmer = buildProducer(producerId);
    Producer producer = buildProducerEntity(producerId, farmer);
    ProducerProfile profile = new ProducerProfile();

    Jwt jwt = buildJwt(farmer.getAuthSub(), farmer.getEmail());

    PaymentMethodRequest pix1 = new PaymentMethodRequest();
    pix1.setType("pix");
    pix1.setPixKeyType("email");
    pix1.setPixKey("joao@email.com");

    PaymentMethodRequest pix2 = new PaymentMethodRequest();
    pix2.setType("pix");
    pix2.setPixKeyType("cpf");
    pix2.setPixKey("12345678901");

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setPaymentMethods(List.of(pix1, pix2));

    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmer);
    when(producerRepository.findDetailedById(producerId)).thenReturn(Optional.of(producer));
    when(userRepository.save(farmer)).thenReturn(farmer);
    when(producerRepository.save(producer)).thenReturn(producer);
    when(producerProfileRepository.findById(producerId)).thenReturn(Optional.of(profile));
    when(producerProfileRepository.save(profile)).thenReturn(profile);
    when(addressRepository.findByUserIdAndIsPrimaryTrue(producerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> producerService.updateProducerProfile(producerId, jwt, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Duplicate payment method type");
  }

  @Test
  void updateProducerProfile_shouldNotTouchPaymentMethods_whenListIsEmpty() {
    UUID producerId = UUID.randomUUID();
    User farmer = buildProducer(producerId);
    Producer producer = buildProducerEntity(producerId, farmer);
    ProducerProfile profile = new ProducerProfile();

    Jwt jwt = buildJwt(farmer.getAuthSub(), farmer.getEmail());

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setPaymentMethods(List.of());

    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmer);
    when(producerRepository.findDetailedById(producerId)).thenReturn(Optional.of(producer));
    when(userRepository.save(farmer)).thenReturn(farmer);
    when(producerRepository.save(producer)).thenReturn(producer);
    when(producerProfileRepository.findById(producerId)).thenReturn(Optional.of(profile));
    when(producerProfileRepository.save(profile)).thenReturn(profile);
    when(addressRepository.findByUserIdAndIsPrimaryTrue(producerId)).thenReturn(Optional.empty());
    when(paymentMethodRepository.findByFarmerIdAndActiveTrue(producerId)).thenReturn(List.of());

    producerService.updateProducerProfile(producerId, jwt, request);

    verify(paymentMethodRepository, never()).save(any(PaymentMethod.class));
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  private User buildProducer(UUID id) {
    return buildUser(id, TypeUser.FARMER, "João Farmer");
  }

  private User buildUser(UUID id, TypeUser type, String name) {
    User user = new User();
    user.setId(id);
    user.setName(name);
    user.setEmail(name.toLowerCase().replace(" ", "") + "@example.com");
    user.setPhone("51988888888");
    user.setType(type);
    user.setActive(true);
    user.setAuthSub("auth-sub-" + id);
    user.setCreatedAt(OffsetDateTime.now().minusDays(1));
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }

  private Producer buildProducerEntity(UUID id, User user) {
    Producer producer = new Producer();
    producer.setId(id);
    producer.setUser(user);
    producer.setFiscalNumber("12345678901");
    producer.setFiscalNumberType("CPF");
    producer.setFarmName("Fazenda Original");
    producer.setTotalReviews(0);
    producer.setAverageRating(BigDecimal.ZERO);
    producer.setTotalOrders(0);
    producer.setTotalSalesAmount(BigDecimal.ZERO);
    return producer;
  }

  private Jwt buildJwt(String sub, String email) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("sub", sub)
        .claim("email", email)
        .build();
  }
}
