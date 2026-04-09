package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProducerServiceTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private ProducerService producerService;

  @Test
  void getAllProducers_shouldReturnAllFarmers() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 10);
    when(userRepository.findAllByTypeAndActiveIsTrue(eq(TypeUser.FARMER), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(buildProducer(id1), buildProducer(id2))));

    Page<ProducerResponse> response = producerService.getAllProducers(pageable);

    assertThat(response.getContent()).hasSize(2);
    assertThat(response.getContent()).extracting(ProducerResponse::getId).containsExactlyInAnyOrder(id1, id2);
  }

  @Test
  void getAllProducers_shouldReturnEmptyList_whenNoFarmersExist() {
    Pageable pageable = PageRequest.of(0, 10);
    when(userRepository.findAllByTypeAndActiveIsTrue(eq(TypeUser.FARMER), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    Page<ProducerResponse> response = producerService.getAllProducers(pageable);

    assertThat(response.getContent()).isEmpty();
  }

  @Test
  void getProducerById_shouldReturnProducerResponse_whenProducerExists() {
    UUID producerId = UUID.randomUUID();
    User producer = buildProducer(producerId);
    when(userRepository.findById(producerId)).thenReturn(Optional.of(producer));

    ProducerResponse response = producerService.getProducerById(producerId);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(producerId);
    assertThat(response.getName()).isEqualTo("João Farmer");
    assertThat(response.getEmail()).isEqualTo(producer.getEmail());
    assertThat(response.getPhone()).isEqualTo(producer.getPhone());
    assertThat(response.isActive()).isTrue();
    assertThat(response.getCreatedAt()).isNotNull();
    assertThat(response.getUpdatedAt()).isNotNull();
  }

  @Test
  void getProducerById_shouldThrowNotFoundException_whenProducerNotFound() {
    UUID producerId = UUID.randomUUID();
    when(userRepository.findById(producerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> producerService.getProducerById(producerId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  @Test
  void getProducerById_shouldThrowNotFoundException_whenUserIsNotFarmer() {
    UUID customerId = UUID.randomUUID();
    User customer = buildUser(customerId, TypeUser.CUSTOMER, "Maria Customer");
    when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

    assertThatThrownBy(() -> producerService.getProducerById(customerId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  @Test
  void getProducerById_shouldThrowNotFoundException_whenUserIsAdmin() {
    UUID adminId = UUID.randomUUID();
    User admin = buildUser(adminId, TypeUser.ADMIN, "Admin User");
    when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> producerService.getProducerById(adminId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  @Test
  void activateProducer_shouldActivateAndReturnResponse_whenProducerExists() {
    UUID producerId = UUID.randomUUID();
    User producer = buildProducer(producerId);
    producer.setActive(false);
    when(userRepository.findById(producerId)).thenReturn(Optional.of(producer));
    when(userRepository.save(producer)).thenReturn(producer);

    ProducerResponse response = producerService.activateProducer(producerId);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(producerId);
    assertThat(response.isActive()).isTrue();
    verify(userRepository).save(producer);
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
}

